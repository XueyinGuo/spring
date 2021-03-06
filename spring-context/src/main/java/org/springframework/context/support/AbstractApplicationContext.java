/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Abstract implementation of the {@link org.springframework.context.ApplicationContext}
 * interface. Doesn't mandate the type of storage used for configuration; simply
 * implements common context functionality. Uses the Template Method design pattern,
 * requiring concrete subclasses to implement abstract methods.
 *
 * <p>In contrast to a plain BeanFactory, an ApplicationContext is supposed
 * to detect special beans defined in its internal bean factory:
 * Therefore, this class automatically registers
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessors},
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessors},
 * and {@link org.springframework.context.ApplicationListener ApplicationListeners}
 * which are defined as beans in the context.
 *
 * <p>A {@link org.springframework.context.MessageSource} may also be supplied
 * as a bean in the context, with the name "messageSource"; otherwise, message
 * resolution is delegated to the parent context. Furthermore, a multicaster
 * for application events can be supplied as an "applicationEventMulticaster" bean
 * of type {@link org.springframework.context.event.ApplicationEventMulticaster}
 * in the context; otherwise, a default multicaster of type
 * {@link org.springframework.context.event.SimpleApplicationEventMulticaster} will be used.
 *
 * <p>Implements resource loading by extending
 * {@link org.springframework.core.io.DefaultResourceLoader}.
 * Consequently treats non-URL resource paths as class path resources
 * (supporting full class path resource names that include the package path,
 * e.g. "mypackage/myresource.dat"), unless the {@link #getResourceByPath}
 * method is overridden in a subclass.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @since January 21, 2001
 * @see #refreshBeanFactory
 * @see #getBeanFactory
 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.context.MessageSource
 */
public abstract class AbstractApplicationContext extends DefaultResourceLoader
		implements ConfigurableApplicationContext {

	/**
	 * Name of the MessageSource bean in the factory.
	 * If none is supplied, message resolution is delegated to the parent.
	 * @see MessageSource
	 */
	public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

	/**
	 * Name of the LifecycleProcessor bean in the factory.
	 * If none is supplied, a DefaultLifecycleProcessor is used.
	 * @see org.springframework.context.LifecycleProcessor
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 */
	public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";

	/**
	 * Name of the ApplicationEventMulticaster bean in the factory.
	 * If none is supplied, a default SimpleApplicationEventMulticaster is used.
	 * @see org.springframework.context.event.ApplicationEventMulticaster
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";


	static {
		// Eagerly load the ContextClosedEvent class to avoid weird classloader issues
		// on application shutdown in WebLogic 8.1. (Reported by Dustin Woods.)
		ContextClosedEvent.class.getName();
	}


	/** Logger used by this class. Available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/** Unique id for this context, if any. */
	/*
	* 创建标识用的 唯一ID
	* AbstractRefreshableConfigApplicationContext中的
	* beanFactory.setSerializationId(getId());就是此处赋的值
	* */
	private String id = ObjectUtils.identityToString(this);

	/** Display name. */
	private String displayName = ObjectUtils.identityToString(this);

	/** Parent context. */
	@Nullable
	private ApplicationContext parent;

	/** Environment used by this context. */
	@Nullable
	private ConfigurableEnvironment environment;

	/** BeanFactoryPostProcessors to apply on refresh. */
	private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();

	/** System time in milliseconds when this context started. */
	private long startupDate;

	/** Flag that indicates whether this context is currently active. */
	private final AtomicBoolean active = new AtomicBoolean();

	/** Flag that indicates whether this context has been closed already. */
	private final AtomicBoolean closed = new AtomicBoolean();

	/** Synchronization monitor for the "refresh" and "destroy". */
	/*
	* 调用refresh的时候为什么要加锁？
	* "refresh" and "destroy"要做同步
	* */
	private final Object startupShutdownMonitor = new Object();

	/** Reference to the JVM shutdown hook, if registered. */
	@Nullable
	private Thread shutdownHook;

	/** ResourcePatternResolver used by this context. */
	private ResourcePatternResolver resourcePatternResolver;

	/** LifecycleProcessor for managing the lifecycle of beans within this context. */
	@Nullable
	private LifecycleProcessor lifecycleProcessor;

	/** MessageSource we delegate our implementation of this interface to. */
	@Nullable
	private MessageSource messageSource;

	/** Helper class used in event publishing. */
	@Nullable
	private ApplicationEventMulticaster applicationEventMulticaster;

	/** Statically specified listeners. */
	private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

	/** Local listeners registered before refresh. */
	@Nullable
	private Set<ApplicationListener<?>> earlyApplicationListeners;

	/** ApplicationEvents published before the multicaster setup. */
	@Nullable
	private Set<ApplicationEvent> earlyApplicationEvents;


	/**
	 * Create a new AbstractApplicationContext with no parent.
	 */
	public AbstractApplicationContext() {
		/*
		* 解析当前系统运行的时候的某些资源，比如我们写的XMl文件
		*
		* */
		this.resourcePatternResolver = getResourcePatternResolver();
	}

	/**
	 * Create a new AbstractApplicationContext with the given parent context.
	 * @param parent the parent context
	 */
	public AbstractApplicationContext(@Nullable ApplicationContext parent) {
		/*
		* this() 创建一个资源加载器，用来加载XML文件
		* setParent(parent) 如果parent不为空，则把父容器和子容器进行融合操作，但是在SpringMVC中会有
		* */
		this();
		setParent(parent);
	}


	//---------------------------------------------------------------------
	// Implementation of ApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the unique id of this application context.
	 * <p>Default is the object id of the context instance, or the name
	 * of the context bean if the context is itself defined as a bean.
	 * @param id the unique id of the context
	 */
	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getApplicationName() {
		return "";
	}

	/**
	 * Set a friendly name for this context.
	 * Typically done during initialization of concrete context implementations.
	 * <p>Default is the object id of the context instance.
	 */
	public void setDisplayName(String displayName) {
		Assert.hasLength(displayName, "Display name must not be empty");
		this.displayName = displayName;
	}

	/**
	 * Return a friendly name for this context.
	 * @return a display name for this context (never {@code null})
	 */
	@Override
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * Return the parent context, or {@code null} if there is no parent
	 * (that is, this context is the root of the context hierarchy).
	 */
	@Override
	@Nullable
	public ApplicationContext getParent() {
		return this.parent;
	}

	/**
	 * Set the {@code Environment} for this application context.
	 * <p>Default value is determined by {@link #createEnvironment()}. Replacing the
	 * default with this method is one option but configuration through {@link
	 * #getEnvironment()} should also be considered. In either case, such modifications
	 * should be performed <em>before</em> {@link #refresh()}.
	 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Return the {@code Environment} for this application context in configurable
	 * form, allowing for further customization.
	 * <p>If none specified, a default environment will be initialized via
	 * {@link #createEnvironment()}.
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			/*
			* 创建一个标准的环境 new StandardEnvironment()
			* StandardEnvironment有一个无参构造方法，会直接调用父类AbstractEnvironment的构造方法
			* 父类构造方法中有
			* 定制化属性资源，这是一个空方法，可以自己扩展实现自己的逻辑
			* StandardEnvironment已经继承了AbstractEnvironment，也已经重写了父类构造方法调用的customizePropertySources方法
			* 重写的方法中调用
			* getSystemProperties() 可以获取到了一些系统当前的配置值和系统环境值
			* 比如环境属性值中就包括username=yanni等信息
			* */
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * Create and return a new {@link StandardEnvironment}.
	 * <p>Subclasses may override this method in order to supply
	 * a custom {@link ConfigurableEnvironment} implementation.
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardEnvironment();
	}

	/**
	 * Return this context's internal bean factory as AutowireCapableBeanFactory,
	 * if already available.
	 * @see #getBeanFactory()
	 */
	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
		return getBeanFactory();
	}

	/**
	 * Return the timestamp (ms) when this context was first loaded.
	 */
	@Override
	public long getStartupDate() {
		return this.startupDate;
	}

	/**
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 * @param event the event to publish (may be application-specific or a
	 * standard framework event)
	 */
	@Override
	public void publishEvent(ApplicationEvent event) {
		/*
		 * 多播器广播容器刷新完成事件，对应监听器执行相应的方法
		 * */
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 * or a payload object to be turned into a {@link PayloadApplicationEvent})
	 */
	@Override
	public void publishEvent(Object event) {
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 * or a payload object to be turned into a {@link PayloadApplicationEvent})
	 * @param eventType the resolved event type, if known
	 * @since 4.2
	 */
	protected void publishEvent(Object event, @Nullable ResolvableType eventType) {
		Assert.notNull(event, "Event must not be null");

		// Decorate event as an ApplicationEvent if necessary
		ApplicationEvent applicationEvent;
		if (event instanceof ApplicationEvent) {
			applicationEvent = (ApplicationEvent) event;
		}
		else {
			applicationEvent = new PayloadApplicationEvent<>(this, event);
			if (eventType == null) {
				eventType = ((PayloadApplicationEvent<?>) applicationEvent).getResolvableType();
			}
		}

		// Multicast right now if possible - or lazily once the multicaster is initialized
		if (this.earlyApplicationEvents != null) {
			this.earlyApplicationEvents.add(applicationEvent);
		}
		else {
			/*
			* 多播器广播容器刷新完成事件，对应监听器执行相应的方法
			* */
			getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);
		}

		// Publish event via parent context as well...
		if (this.parent != null) {
			if (this.parent instanceof AbstractApplicationContext) {
				((AbstractApplicationContext) this.parent).publishEvent(event, eventType);
			}
			else {
				this.parent.publishEvent(event);
			}
		}
	}

	/**
	 * Return the internal ApplicationEventMulticaster used by the context.
	 * @return the internal ApplicationEventMulticaster (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
		if (this.applicationEventMulticaster == null) {
			throw new IllegalStateException("ApplicationEventMulticaster not initialized - " +
					"call 'refresh' before multicasting events via the context: " + this);
		}
		return this.applicationEventMulticaster;
	}

	/**
	 * Return the internal LifecycleProcessor used by the context.
	 * @return the internal LifecycleProcessor (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
		if (this.lifecycleProcessor == null) {
			throw new IllegalStateException("LifecycleProcessor not initialized - " +
					"call 'refresh' before invoking lifecycle methods via the context: " + this);
		}
		return this.lifecycleProcessor;
	}

	/**
	 * Return the ResourcePatternResolver to use for resolving location patterns
	 * into Resource instances. Default is a
	 * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver},
	 * supporting Ant-style location patterns.
	 * <p>Can be overridden in subclasses, for extended resolution strategies,
	 * for example in a web environment.
	 * <p><b>Do not call this when needing to resolve a location pattern.</b>
	 * Call the context's {@code getResources} method instead, which
	 * will delegate to the ResourcePatternResolver.
	 * @return the ResourcePatternResolver for this context
	 * @see #getResources
	 * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver
	 */
	protected ResourcePatternResolver getResourcePatternResolver() {
		/*
		* 创建一个资源加载器，用来加载XML文件
		* */
		return new PathMatchingResourcePatternResolver(this);
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the parent of this application context.
	 * <p>The parent {@linkplain ApplicationContext#getEnvironment() environment} is
	 * {@linkplain ConfigurableEnvironment#merge(ConfigurableEnvironment) merged} with
	 * this (child) application context environment if the parent is non-{@code null} and
	 * its environment is an instance of {@link ConfigurableEnvironment}.
	 * @see ConfigurableEnvironment#merge(ConfigurableEnvironment)
	 */
	@Override
	public void setParent(@Nullable ApplicationContext parent) {
		/*
		* private ApplicationContext parent;
		* 如果parent不为空，则把父容器和子容器进行融合操作，但是在SpringMVC中会有
		* */
		this.parent = parent;
		if (parent != null) {
			Environment parentEnvironment = parent.getEnvironment();
			if (parentEnvironment instanceof ConfigurableEnvironment) {
				getEnvironment().merge((ConfigurableEnvironment) parentEnvironment);
			}
		}
	}

	@Override
	public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
		Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
		this.beanFactoryPostProcessors.add(postProcessor);
	}

	/**
	 * Return the list of BeanFactoryPostProcessors that will get applied
	 * to the internal BeanFactory.
	 */
	public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
		return this.beanFactoryPostProcessors;
	}

	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		Assert.notNull(listener, "ApplicationListener must not be null");
		if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.addApplicationListener(listener);
		}
		this.applicationListeners.add(listener);
	}

	/**
	 * Return the list of statically specified ApplicationListeners.
	 */
	public Collection<ApplicationListener<?>> getApplicationListeners() {
		return this.applicationListeners;
	}

	@Override
	public void refresh() throws BeansException, IllegalStateException {
		/*
		 * 调用refresh的时候为什么要加锁？
		 * "refresh" and "destroy"要做同步
		 * */
		synchronized (this.startupShutdownMonitor) {
			// Prepare this context for refreshing.
			/*
			* //各种准备工作!!!!!!!!!!!!!!!
			* 设置容器启动时间
			* 设置活跃标志位为true，设置关闭标志位为false
			* 设置Environment对象，设置监听器为空集合
			*
			* 其中有一个我们可以自己扩展的方法，叫做 initPropertySources()
			* 如果我们自己扩展实现了一些 getEnvironment().setRequiredProperties(String... str)
			* 可以在 prepareRefresh(); 的 getEnvironment().validateRequiredProperties();中
            * 得到验证，如果没有设置的属性则直接抛出异常
            * 如果非得要有一个莫名其妙的参数，可以通过 -D参数指定， 比如 -D abc=def
			* */

			prepareRefresh();

			// Tell the subclass to refresh the internal bean factory.
			/*
			* beanFactory是一个bean容器，BeanFactory是根接口。
			* beanFactory.setSerializationId(getId()); customizeBeanFactory(beanFactory);这两部是干什么？？？？？？？？？？？？？？？？
			* 此方法调用时确保会有一个新的beanFactory，先close掉旧的，后创建新的。
			* 然后加载bean的定义信息（BeanDefinition【应该就是XML中定义的那些bean的信息吧？】）
			*
			* 1.
			* 创建一个新的DefaultListableBeanFactory对象
			* 设置了一些Aware接口的忽略
			* 还设置了一些比较重要的属性值
			* 比如：allowBeanDefinitionOverriding 和 allowCircularReference，
			* 除了一些关键位置外，其余的还都是默认值
			*
			* 2.
			* 创建完新的BeanFactory之后，为其指定了一个序列化ID
			* 以便在之后的反序列时候通过这个ID直接得到Bean工厂对象
			*
			* 3.
			* customizeBeanFactory定制化自己的工厂：
			* 一个扩展点，重写这个方法就完了！！！！！！
			*
			* ==============================================================================================
			* ==============================================================================================
			* 4.============================================================================================
			* 加载xml配置文件的，我们的bean定义信息需要从xml提取出来存进工厂类，
			* loadBeanDefinitions()  方法重载好多次！！！！！！ ，String[]就是所有需要解析的XML文档的文件名
			* 总的流程就是从 String[] -> String -> Resource[] -> Resource -> Document -> 然后解析 Document
			* -> 解析成 BeanDefinition对象 -> 再把解析好的BeanDefinition包装成 BeanDefinitionHolder
			* -> 注册进 BeanFactory
			*
			* Document的解析过程如下所示：
			* 		4.0 Document 由输入流读入
			* 		4.1 创建一个单独的解析器来进行一些解析处理工作 delegate
			* 		4.2 查看输入输入流的标签是否是默认的命名空间中的元素，然后获取各个子标签
			* 		4.3 开始解析原生标签（拿 <bean> 标签来说）
			* 			4.3.1  解析得到 BeanDefinitionHolder  有完整beanDefinition定义信息的 beanName 和 别名
			* 			4.3.2  向IOC容器注册解析到的 beanDefinition   将给定的beanDefinition注册到给定的bean工厂
			* 		4.4 解析导入的标签
			* 			4.4.1
			* 		TODO 完成自定义标签的注释
			* 5.如果打开了 <context:component-scan base-package="。。。"></context:component-scan>注解扫描
			*  进行一些 internal 的赋值操作
			*	    @Component, @Repository, @Service,
			*       @Controller, @RestController, @ControllerAdvice, and @Configuration
			*       将带有这些注解的类包装成 beanDefinition 放入 beanFactory
			*       方便之后从 beanFactory 直接获取 然后更详细的解析！！！！！！！！！
			*  org.springframework.context.annotation.internalConfigurationAnnotationProcessor 找不到任何匹配类之后
			*  直接 fallback 成 ConfigurationClassPostProcessor ， 然而 ConfigurationClassPostProcessor 实现了 PriorityOrdered 和 BeanDefinitionRegistryPostProcessor
			*  可以用 “org.springframework.context.annotation.internalConfigurationAnnotationProcessor” 获得到 ConfigurationClassPostProcessor的bean对象， 加入到集合中
			*  所以之后可以执行 ConfigurationClassPostProcessor 类中的 processConfigBeanDefinitions() 的方法 使注解们得到更进一步的处理
			* ==============================================================================================
			* ==============================================================================================
			* ==============================================================================================
			* 6. 然后就可以进行实例化操作了
			* */
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

			// Prepare the bean factory for use in this context.
			/*
			* 设置了很多的ignoredDependencyInterfaces，但是忽略的都是Aware接口，为什么？ Aware接口都是干什么的？？？？？？
			* 设置beanFactory的类加载器等等信息，
			* 还有BeanPostProcessor，初始化了很多操作
			* */
			prepareBeanFactory(beanFactory);

			try {
				// Allows post-processing of the bean factory in context subclasses.
				/*
				* 这是一个空方法！！！
				* 模板模式，具体实现由子类去自己操控
				*
				* 自己操作 beanFactory 对象，想改什么改什么，想怎么改就怎么改
				*
				* 比如我们就可以在这里加入 自定义的 BeanFactoryPostProcessor
				* */
				postProcessBeanFactory(beanFactory);

				// Invoke factory processors registered as beans in the context.
				/*
				* 初始化所有的后置处理器（BeanFactoryPostProcessor），所有的后置处理器也都是bean
				* 只不过bean分两类：一类是普通的自己用的对象，一类是Spring要用的容器对象
				* 但是此方法必须在单例的实例化之前调用！！！！ 为什么要在单例的实例化之前调用？而不是所有的对象的实例化之前
				* 难道说Spring的容器对象都是单例的吗？？？？？
				* */
				/*
				 * 获取到当前应用程序上下文的 beanFactoryPostProcessors 变量的值。并且实例化调用执行所有的已经注册的 beanFactoryPostProcessor
				 * 默认情况下通过 getBeanFactoryPostProcessors() 来获取已经注册的 BFPP ，但是默认是空的
				 *
				 * BeanFactoryPostProcessor
				 *
				 * BeanDefinitionRegistryPostProcessor
				 *
				 * 上边二者的关系是： BeanDefinitionRegistryPostProcessor 继承了 BeanFactoryPostProcessor 接口
				 * 但是 BeanDefinitionRegistryPostProcessor 是操作 beanDefinition 的，
				 * beanFactory 中有非常重要的两个集合 ： beanDefinitionMap 和 beanDefinitionNames
				 *
				 * BeanDefinitionRegistryPostProcessor 是对这两个集合的增删改查操作
				 * 		void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
				 * 		此方法是 BeanDefinitionRegistryPostProcessor中干活的方法，参数是一个 BeanDefinitionRegistry 类型
				 *
				 * BeanFactoryPostProcessor 中包含了 beanDefinition的两个集合
				 * 		void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
				 * 		此方法是 BeanFactoryPostProcessor 干活的方法， 可以做更多的操作！ 完成了 BeanDefinitionRegistry 之外的功能
				 * */

				/*
				 * getBeanFactoryPostProcessors() 默认是空， 既然有get 那就肯定有set或者add
				 * */
				/*
				 * invokeBeanFactoryPostProcessors() 的执行逻辑
				 *
				 * BeanDefinitionRegistryPostProcessor 继承了  BeanFactoryPostProcessor，
				 * 所以 前者是后者的子集，处理逻辑是：
				 * 1.先处理 getBeanFactoryPostProcessors() 方法 get 到的， 因为这部分是用户自己加进去的！
				 * 		加的方式有两种： 1.在重写 customizeBeanFactory 方法的时候直接调用父类的 addBeanFactoryPostProcessor()，
				 * 		   			  2.在配置文件中直接写一个 <bean>
				 *
				 * 2.再处理实现了 BeanDefinitionRegistryPostProcessor 接口的 PostProcessor类
				 * 3.最后处理实现了 BeanFactoryPostProcessor 接口的类
				 * */
				/*
				 * 然后还会进行注解的处理工作，配置类的定义信息
				 * */
				/*
				 * **********如果从配置文件读取的 beanDefinition，他的类型是 GenericBeanDefinition
				 * **********如果是注解扫描到的 beanDefinition， 他的类型是 ScannedGenericBeanDefinition
				 * **********ScannedGenericBeanDefinition 实现了 AnnotatedBeanDefinition
				 *
				 * 从标注了 @Component, @Repository, @Service, @Controller, @RestController, @ControllerAdvice, and
				 * @Configuration 的 candidates 中解析 又标注了
				 * @Import @ImportResource @ComponentScan @ComponentScans @Bean 的 */
				/*
				 * 经过一系列判断之后开始进行注解的处理工作
				 * 1.先处理内部类，处理内部类的最后 还是调用 doProcessConfigurationClass() 方法
				 * 2.处理属性资源文件， 加了@PropertySource 的注解
				 * 3.处理@ComponentScan 或者 @ComponentScans 注解
				 * 4.处理加了 @Import 的 bean，用了一个比较麻烦的方法 processImports()
				 * 		4.1 遍历每个加了 @Import 的类
				 * 		4.2 被import进来的类也可能加了 @Import，所以递归一下
				 * 5.处理 @ImportResource 引入的配置文件
				 * 6.处理加了 @Bean 的方法
				 * */
				/*
				* 如果有数据库配置 文件， ${jdbc.userName}这种属性值，在一个 PropertySourcesPlaceholderConfigurer 中进行解析替换工作
				* */
				invokeBeanFactoryPostProcessors(beanFactory);

				// Register bean processors that intercept bean creation.
				/*
				 * 注册所有的后置处理器（BeanPostProcessor）
				 * bean分两类：一类是普通的自己用的对象，一类是Spring要用的容器对象
				 * 这里已经是beanPostProcessor了，不再是上边的BeanFactoryProcessor了
				 * 但是此方法必须在所有的普通bean被实例化之前调用！！！
				 * */
				registerBeanPostProcessors(beanFactory);

				// Initialize message source for this context.
				/*
				* i18n国际化的操作
				* */
				initMessageSource();

				// Initialize event multicaster for this context. 初始化一个广播器
				/*
				* 初始化事件监听的多路广播器，使用了观察者模式，但是这个观察者模式跟普通的不一样：
				* Spring把普通观察者模式进行了更细粒度的划分：事件的发布订阅模式
				* 1.事件源：（类比气象站检测到数据变化）谁来调用或者执行发布具体的通知
				* 2.多播器：（气象站检测到数据变化，遍历订阅者列表推送新的数据时候的 for 循环抽象出来了多播器）
				* 		遍历的操作拿出来之后委托给一个多播器进行消息通知，或者通过观察者进行不同的操作
				* 3.监听器：（各个订阅天气数据的用户）
				* 		接受不同的事件来做不同的处理工作
				* 4.事件： 被观察者具体执行的动作
				*
				* 执行过程：
				* 事件源发布不同的事件，当发布之后会调用多播器的方法来进行事件的广播操作，由多播器出发具体的监听器的执行操作
				* 监听器接收的具体事件之后，可以验证匹配是否能处理当前事件，可以的话直接处理，不能的话就不做操作
				* */
				initApplicationEventMulticaster();

				// Initialize other special beans in specific context subclasses.
				/*
				* 模板模式，具体实现由子类去自己操控
				* 留给子类初始化其他的Bean
				* */
				onRefresh();

				// Check for listener beans and register them.注册监听器
				/*
				* 在所有注册的Bean中查找 listenerBean，注册到消息广播器中
				* */
				registerListeners();

				// Instantiate all remaining (non-lazy-init) singletons.
				/*
				* 创建对象的核心流程！！！！！！！！！！！！！！！！！
				* 创建剩下的单例对象，这些对象都是非懒加载的
				*
				* 创建对象的五种方式：
				* 1.自定义BeanPostProcessor，生成代理对象： resolveBeforeInstantiation包下的例子，就是 InstantiationAwareBeanPostProcessor
				* 2.通过反射创建对象
				* 3.通过FactoryMethod创建对象:
				* 4.通过FactoryBean创建对象: getObject()   已经抽象成了接口规范，所有对象都需要用getObject方法获取
				* 5.通过Supplier创建对象 ：
				* 				本来我们可以直接反射拿到该对象，但是用Supplier的话需要在BeanFactoryPostProcessor中做一些操作,
								操作的BeanDefinition，
								BeanDefinition有两个主要的实现子类： GenericBeanDefinition， RootBeanDefinition
								GenericBeanDefinition 继承了抽象类 AbstractBeanDefinition， 抽象类中有直接设置Supplier的方法，
								所以Bean标签没转换成RootBeanDefinition之前就可以直接设置 Supplier

								可以随便定义创建对象的方法，不止局限于getObject直接new，也可以在自己的逻辑中实现反射获取，或者工厂获取
				* */
				/*
				* 实现自定义的 Converter 只能继承三个接口，
				* 如何添加自定义的 Converter，
				* <bean id="studentConverter" class="com.szu.spring.myConverter.StudentConverter"></bean>
 					<bean id="conversionService" class="org.springframework.context.support.ConversionServiceFactoryBean">
					<property name="converters">
						<set>
							<ref bean="studentConverter"></ref>
						</set>
					</property>
				  </bean>
				* */
				/*
				 * 合并父类BeanDefinition
				 * 一开始创建的BeanDefinition 都是属于两个类型 ： GenericBeanDefinition  RootBeanDefinition
				 *
				 * getMergedLocalBeanDefinition  就是要在实例化之前，把所有的基础的BeanDefinition对象转成RootBeanDefinition并进行缓存
				 * 在后续马上进行实例化的时候直接获取定义信息，而定义信息中如果包含了父类，那么必须要先创建父类才能创建子类型
				 * */
				/*
				 * 检查beanName对应的mergedBeanDefinition是否存在于缓存中，次缓存是在BeanFactoryPostProcessor中添加的
				 * 所以是在哪里添加的呢？
				 * 在invokeBeanFactoryPostProcessor()方法中的 beanFactory.getBeanNamesForType()!!!!!!!!!!!!!!!!!!
				 * */
				/* //判断Bean有没有实现FactoryBean接口
				 * ======================================================================================
				 * ======================================================================================
				 * BeanFactory 和 FactoryBean 的区别： 他们都是工厂对象，都是用来创建对象的！！！！
				 * 		1.如果使用 BeanFactory，那么必须遵守SpringBean的生命周期，从实例化到初始化，invokeAwareMethod
				 * 			invokeInitMethod，before，after此流程，过程非常复杂
				 * 		2.FactoryBean更加简单，
				 * 			2.1 isSingleton()：判断是否单例，如果返回false，则getObject（）获取的对象不放入缓存 factoryBeanObjectCache
				 * 			2.2 getObject()：直接返回对象
				 * 			2.3 getObjectType():返回类型
				 *
				 * 我们在使用FactoryBean接口创建对象的时候，一共创建了两个对象：
				 *  1.实现了factoryBean接口的子类对象   2.通过Object方法返回的对象
				 * 两个对象都交给了Spring来管理
				 * 虽然都交给了Spring管理，但是放的空间不是一个
				 * factoryBean接口的子类对象放在了一级缓存，isSingleton()返回false的话，Spring就不管了，因为都不放入缓存了
				 * （一级缓存：singletonObjects   二级缓存：earlySingletonObjects  三级缓存：singletonFactories）
				 * 通过Object方法返回的对象 放在了 factoryBeanObjectCache 中
				 * **************************************************************************************
				 * ======================================================================================
				 * */
				/*
				 * =============================================================================
				 * bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
				 * 返回对象实例！！！！！！
				 * 如果是 FactoryBean 类型则简单设置一下就直接返回，
				 * 如果是其他类型，则最终去调用 实现FactoryBean接口的子类中的 getObject() 方法返回对象实例
				 * =============================================================================
				 *  */
				finishBeanFactoryInitialization(beanFactory);

				// Last step: publish corresponding event.
				/*
				* 完成刷新，
				* 执行一些Bean的生命周期方法
				* */
				finishRefresh();
			}

			catch (BeansException ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Exception encountered during context initialization - " +
							"cancelling refresh attempt: " + ex);
				}

				// Destroy already created singletons to avoid dangling resources.
				destroyBeans();

				// Reset 'active' flag.
				cancelRefresh(ex);

				// Propagate exception to caller.
				throw ex;
			}

			finally {
				// Reset common introspection caches in Spring's core, since we
				// might not ever need metadata for singleton beans anymore...
				/*
				* 重置缓存
				* */
				resetCommonCaches();
			}
		}
	}

	/**
	 * Prepare this context for refreshing, setting its startup date and
	 * active flag as well as performing any initialization of property sources.
	 */
	protected void prepareRefresh() {
		// Switch to active.
		/*
		* 记录启动时间，设置关闭标志位为false，设置启动标志位为true
		* 表明当前容器是在运行状态
		* */
		this.startupDate = System.currentTimeMillis();
		this.closed.set(false);
		this.active.set(true);

		if (logger.isDebugEnabled()) {
			if (logger.isTraceEnabled()) {
				logger.trace("Refreshing " + this);
			}
			else {
				logger.debug("Refreshing " + getDisplayName());
			}
		}

		// Initialize any placeholder property sources in the context environment.
		/*
		* 这里是个空方法，可以自己去继承父类实现 initPropertySources()
		* 可以继承ClassPathXmlApplicationContext类重写该方法
		*
		* public class MyClassPathXmlApplicationContext extends ClassPathXmlApplicationContext{
		* 		构造方法（String... configLocations）{
		* 			super.(configLocations)
		* 		}
		*
		* 		@Override
		* 		protected void initPropertySources(){
		* 			自己扩展
		* 		}
		* }
		*
		* 如果对于我们的web应用也可以有自己的扩展实现，
		* @see org.springframework.web.context.support.WebApplicationContextUtils#initServletPropertySources
		* */
		/*
		* 如果我们自己扩展实现了一些 setRequiredProperties(String... str)
		* 可以在下一步的 getEnvironment().validateRequiredProperties();中
		* 得到验证，如果没有设置的属性则直接抛出异常
		* 如果非得要有一个莫名其妙的参数，可以通过 -D参数指定， 比如 -D abc=def
		* */
		initPropertySources();

		// Validate that all properties marked as required are resolvable:
		// see ConfigurablePropertyResolver#setRequiredProperties
		getEnvironment().validateRequiredProperties();

		// Store pre-refresh ApplicationListeners...
		/*
		* 判断刷新前的应用程序监听器集合是否为空，如果为空，则将监听器添加到此集合中
		* 如果不等于空，则清空所有集合元素，
		* 为啥要进行判断呢？
		* 因为有些时候这些集合并不为空，虽然在单纯的Spring中是空
		* 但是在SpringBoot中 applicationListeners 就不是空的了，这是为了之后方便扩展吧
		* 然后就可以吧applicationListeners中的监听器们放入到 earlyApplicationListeners早期监听器集合中了
		* 如果想让代码生效，则只需要在当前的applicationListeners设置一些属性值就好了
		*
		* 所以此处也是扩展功能的实现，扩展也是为了之后的上层项目的扩展，单独的Spring项目并不需要折样的扩展
		* */
		if (this.earlyApplicationListeners == null) {
			this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
		}
		else {
			// Reset local application listeners to pre-refresh state.
			this.applicationListeners.clear();
			this.applicationListeners.addAll(this.earlyApplicationListeners);
		}

		// Allow for the collection of early ApplicationEvents,
		// to be published once the multicaster is available...
		/*
		* 创建刷新前的监听事件集合
		* 这里是监听事件集合，上边那些事监听器集合
		* 一个Events，一个Listeners，不是一种东西
		* */
		this.earlyApplicationEvents = new LinkedHashSet<>();
	}

	/**
	 * <p>Replace any stub property sources with actual instances.
	 * @see org.springframework.core.env.PropertySource.StubPropertySource
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#initServletPropertySources
	 */
	protected void initPropertySources() {
		// For subclasses: do nothing by default.
	}

	/**
	 * Tell the subclass to refresh the internal bean factory.
	 * @return the fresh BeanFactory instance
	 * @see #refreshBeanFactory()
	 * @see #getBeanFactory()
	 */
	protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		/*
		* 创建一个新的工厂，并且为工厂做了一下操作：
		* 设置了一些Aware接口的忽略
        * 还设置了一些比较重要的属性值
        * 比如：allowBeanDefinitionOverriding 和 allowCircularReference都是赋值默认值true
        * 如果想要自己扩展，可以重写customizeBeanFactory方法进行覆盖，
        * 这里还有一个重中之重的操作就是 loadBeanDefinitions()!!!
		* */
		refreshBeanFactory();
		return getBeanFactory();
	}

	/**
	 * Configure the factory's standard context characteristics,
	 * such as the context's ClassLoader and post-processors.
	 * @param beanFactory the BeanFactory to configure
	 */
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// Tell the internal bean factory to use the context's class loader etc.
		/*
		* 设置 beanFactory 的classLoader为当前的context的classLoader
		* */
		beanFactory.setBeanClassLoader(getClassLoader());
		/*
		* BeanExpressionResolver 是 SpEL表达式的处理类   #{}   ${}
		* 处理类内部 是一个 SpEL表达式的解析类
		* 解析类内部 是 该解析类的配置类
		*
		* 最外层是一个处理器类 -> 解析器类 -> 解析器的配置类
		*
		* TODO 理清楚自定义解析器的处理流程
		* */
		beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

		// Configure the bean factory with context callbacks.
		/*
		* 完成一些 Aware 对象的注入
		* */
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
		/*
		* 设置要忽略的自动装配接口  TODO ignoreDependencyInterface
		* */
		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

		// BeanFactory interface not registered as resolvable type in a plain factory.
		// MessageSource registered (and found for autowiring) as a bean.
		/*
		* 设置几个自动装配的特殊规则， 当在进行IOC初始化的时候，如果有多个实现，那么就使用指定的对象进行注入
		* 就像 @Primary 注解
		* */
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);

		// Register early post-processor for detecting inner beans as ApplicationListeners.
		/*
		* 注册 BPP
		* */
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

		// Detect a LoadTimeWeaver and prepare for weaving, if found.
		/*
		* 增加对AspectJ的支持，在Java中分为三种织入方式，编译期织入，类加载期织入，运行期织入，在编译器织入是指在Java编译时采用特殊的编译器，将
		* 切面织入到Java类中，而类加载期织入是指类字节码加载到JVM时织入切面，运行期织入则是采用Cglib和JDK进行织入，
		* aspectJ提供了两种织入方式，在编译期将aspectJ语言编写的切面类织入到Java类中，第二种是类加载期织入，
		* 就是下面看到的这个 loadTimeWeaver
		*
		* 这就是提前设置了 AOP 的处理工作
		* */
		if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			// Set a temporary ClassLoader for type matching.
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		// Register default environment beans.
		/*
		* 给 beanFactory 放入一些属性值
		* environment  systemEnvironment  systemProperties
		* */
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
	}

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. All bean definitions will have been loaded, but no beans
	 * will have been instantiated yet. This allows for registering special
	 * BeanPostProcessors etc in certain ApplicationContext implementations.
	 * @param beanFactory the bean factory used by the application context
	 */
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
	}

	/**
	 * 实例化并且执行
	 * Instantiate and invoke all registered BeanFactoryPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before singleton instantiation.
	 */
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		/*
		* 获取到当前应用程序上下文的 beanFactoryPostProcessors 变量的值。并且实例化调用执行所有的已经注册的 beanFactoryPostProcessor
		* 默认情况下通过 getBeanFactoryPostProcessors() 来获取已经注册的 BFPP ，但是默认是空的
		*
		* BeanFactoryPostProcessor
		*
		* BeanDefinitionRegistryPostProcessor
		*
		* 上边二者的关系是： BeanDefinitionRegistryPostProcessor 继承了 BeanFactoryPostProcessor 接口
		* 但是 BeanDefinitionRegistryPostProcessor 是操作 beanDefinition 的，
		* beanFactory 中有非常重要的两个集合 ： beanDefinitionMap 和 beanDefinitionNames
		*
		* BeanDefinitionRegistryPostProcessor 是对这两个集合的增删改查操作
		* 		void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
		* 		此方法是 BeanDefinitionRegistryPostProcessor中干活的方法，参数是一个 BeanDefinitionRegistry 类型
		*
		* BeanFactoryPostProcessor 中包含了 beanDefinition的两个集合
		* 		void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
		* 		此方法是 BeanFactoryPostProcessor 干活的方法， 可以做更多的操作！ 完成了 BeanDefinitionRegistry 之外的功能
		* */

		/*
		* getBeanFactoryPostProcessors() 默认是空， 既然有get 那就肯定有set或者add
		* */
		/*
		* invokeBeanFactoryPostProcessors() 的执行逻辑
		*
		* BeanDefinitionRegistryPostProcessor 继承了  BeanFactoryPostProcessor，
		* 所以 前者是后者的子集，处理逻辑是：
		* 1.先处理 getBeanFactoryPostProcessors() 方法 get 到的， 因为这部分是用户自己加进去的！
		* 		加的方式有两种： 1.在重写 customizeBeanFactory 方法的时候直接调用父类的 addBeanFactoryPostProcessor()，
		* 		   			  2.在配置文件中直接写一个 <bean>
		*					  但是只有第一种可以被 getBeanFactoryPostProcessors() 获取到， 第二种是作为一个BeanDefinition存在于BeanDefinitionMap中
		* 2.再处理实现了 BeanDefinitionRegistryPostProcessor 接口的 PostProcessor类
		* 3.最后处理实现了 BeanFactoryPostProcessor 接口的类
		* */
		/*
		 * 然后还会进行注解的处理工作，配置类的定义信息
		 * */
		/*
		 * =============================================
		 * invokeBeanDefinitionRegistryPostProcessor（）
		 * =============================================
		 * 从标注了 @Component, @Repository, @Service, @Controller, @RestController, @ControllerAdvice, and
		 * @Configuration 的 candidates 中解析 又标注了
		 * @Import @ImportResource @ComponentScan @ComponentScans @Bean 的 */
		/*
		 * 经过一系列判断之后开始进行注解的处理工作
		 * 1.先处理内部类，处理内部类的最后 还是调用 doProcessConfigurationClass() 方法
		 * 2.处理属性资源文件， 加了@PropertySource 的注解
		 * 3.处理@ComponentScan 或者 @ComponentScans 注解
		 * 4.处理加了 @Import 的 bean，用了一个比较麻烦的方法 processImports()
		 * 		4.1 遍历每个加了 @Import 的类
		 * 		4.2 被import进来的类也可能加了 @Import，所以递归一下
		 * 5.处理 @ImportResource 引入的配置文件
		 * 6.处理加了 @Bean 的方法
		 * */
		/*
		 * =============================================
		 * invokeBeanFactoryPostProcessor（）
		 * =============================================
		 * 在此方法中创建所有的 @Configuration 注解标注的配置类，并给这些创建！！！代理对象！！
		 * */
		/*
		 * 在之前扫描注解的时候，如果那个类被@Configuration修饰，则把他的对应的BeanDefinition的
		 * configurationClass 属性设置为了 “full”
		 * 不是配置类的设置为了 “lite”
		 * 当遍历到的BeanDefinition是full的时候，也就是说这是个 配置类，
		 * 然而配置类中的所有属性都应该是单例的，
		 *
		 * 所以当出现这种情况的时候： com.szu.spring.txTest.annotation.MyConfiguration 中这样的情况的时候
		 * 创建代理类来保证配置类中的每个 Bean 都是单例的
		 * */
		/*
		 *
		 * Supplier 的设置
		 * BeanDefinition有两个主要的实现子类： GenericBeanDefinition， RootBeanDefinition
		 *
		 * GenericBeanDefinition 继承了抽象类 AbstractBeanDefinition， 抽象类中有直接设置 Supplier 的方法，
		 * 所以Bean没转换成RootBeanDefinition之前就可以直接设置 Supplier
		 * */
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		// Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
		// (e.g. through an @Bean method registered by ConfigurationClassPostProcessor)
		if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}

	/**
	 * 实例化并且注册，跟上边的那个方法不一样
	 * Instantiate and register all BeanPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before any instantiation of application beans.
	 */
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
	}

	/**
	 * Initialize the MessageSource.
	 * Use parent's if none defined in this context.
	 */
	protected void initMessageSource() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
			this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
			// Make MessageSource aware of parent MessageSource.
			if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
				HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
				if (hms.getParentMessageSource() == null) {
					// Only set parent context as parent MessageSource if no parent MessageSource
					// registered already.
					hms.setParentMessageSource(getInternalParentMessageSource());
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Using MessageSource [" + this.messageSource + "]");
			}
		}
		else {
			// Use empty MessageSource to be able to accept getMessage calls.
			DelegatingMessageSource dms = new DelegatingMessageSource();
			dms.setParentMessageSource(getInternalParentMessageSource());
			this.messageSource = dms;
			beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + MESSAGE_SOURCE_BEAN_NAME + "' bean, using [" + this.messageSource + "]");
			}
		}
	}

	/**
	 * Initialize the ApplicationEventMulticaster.
	 * Uses SimpleApplicationEventMulticaster if none defined in the context.
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
			}
		}
		else {
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + APPLICATION_EVENT_MULTICASTER_BEAN_NAME + "' bean, using " +
						"[" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the LifecycleProcessor.
	 * Uses DefaultLifecycleProcessor if none defined in the context.
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 */
	protected void initLifecycleProcessor() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
			this.lifecycleProcessor =
					beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using LifecycleProcessor [" + this.lifecycleProcessor + "]");
			}
		}
		else {
			DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
			defaultProcessor.setBeanFactory(beanFactory);
			this.lifecycleProcessor = defaultProcessor;
			beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + LIFECYCLE_PROCESSOR_BEAN_NAME + "' bean, using " +
						"[" + this.lifecycleProcessor.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Template method which can be overridden to add context-specific refresh work.
	 * Called on initialization of special beans, before instantiation of singletons.
	 * <p>This implementation is empty.
	 * @throws BeansException in case of errors
	 * @see #refresh()
	 */
	protected void onRefresh() throws BeansException {
		// For subclasses: do nothing by default.
	}

	/**
	 * Add beans that implement ApplicationListener as listeners.
	 * Doesn't affect other listeners, which can be added without being beans.
	 */
	protected void registerListeners() {
		// Register statically specified listeners first.
		/*
		* 创建了
		* applicationListenerBeans
		* applicationListeners
		* */
		for (ApplicationListener<?> listener : getApplicationListeners()) {
			getApplicationEventMulticaster().addApplicationListener(listener);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let post-processors apply to them!
		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
		for (String listenerBeanName : listenerBeanNames) {
			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
		}

		// Publish early application events now that we finally have a multicaster...
		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
		this.earlyApplicationEvents = null;
		if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
				getApplicationEventMulticaster().multicastEvent(earlyEvent);
			}
		}
	}

	/**
	 * Finish the initialization of this context's bean factory,
	 * initializing all remaining singleton beans.
	 */
	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		// Initialize conversion service for this context. 设置类型转换的操作
		/*
		* 实现自定义的 Converter 只能继承三个接口，
		* 如何添加自定义的 Converter，
		* <bean id="studentConverter" class="com.szu.spring.myConverter.StudentConverter"></bean>
 			<bean id="conversionService" class="org.springframework.context.support.ConversionServiceFactoryBean">
			<property name="converters">
				<set>
					<ref bean="studentConverter"></ref>
				</set>
			</property>
		  </bean>
		* */
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
			/*
			* 设置对象的转换服务
			*
			* 字符串类型的 “1” 转换为 Integer 1
			* */
			beanFactory.setConversionService(
					beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		// Register a default embedded value resolver if no bean post-processor
		// (such as a PropertyPlaceholderConfigurer bean) registered any before:
		// at this point, primarily for resolution in annotation attribute values.
		/*
		* 如果没有值处理器，给他一个默认的嵌入值处理器，比如 ${...}  (PropertyPlaceholderConfigurer)
		*
		* <bean class="org.springframework.context.support.PropertySourcesPlaceholderConfigurer"></bean>
		*
		* invokeBeanFactoryPostProcessors(beanFactory);中进行内嵌值处理器的赋值操作
		* */
		if (!beanFactory.hasEmbeddedValueResolver()) {
			beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
		}

		// Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
		/*
		* AOP 相关
		* */
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			getBean(weaverAwareName);
		}

		// Stop using the temporary ClassLoader for type matching.
		beanFactory.setTempClassLoader(null);

		// Allow for caching all bean definition metadata, not expecting further changes.
		/* 所有的beanDefinition已经全部加载完毕，不希望beanDefinition再有什么改变 */
		beanFactory.freezeConfiguration();

		// Instantiate all remaining (non-lazy-init) singletons.
		/*
		 * =================================================================================================
		 * =================================================================================================
		 * 创建对象的核心流程！！！！！！！！！！！！！！！！！
		 * =================================================================================================
		 * =================================================================================================
		 * */
		beanFactory.preInstantiateSingletons();
	}

	/**
	 * Finish the refresh of this context, invoking the LifecycleProcessor's
	 * onRefresh() method and publishing the
	 * {@link org.springframework.context.event.ContextRefreshedEvent}.
	 */
	protected void finishRefresh() {
		// Clear context-level resource caches (such as ASM metadata from scanning).
		clearResourceCaches();

		// Initialize lifecycle processor for this context.
		initLifecycleProcessor();

		// Propagate refresh to lifecycle processor first.
		getLifecycleProcessor().onRefresh();

		// Publish the final event.
		/*
		* 容器刷新完成了，发布事件
		* */
		publishEvent(new ContextRefreshedEvent(this));

		// Participate in LiveBeansView MBean, if active.
		LiveBeansView.registerApplicationContext(this);
	}

	/**
	 * Cancel this context's refresh attempt, resetting the {@code active} flag
	 * after an exception got thrown.
	 * @param ex the exception that led to the cancellation
	 */
	protected void cancelRefresh(BeansException ex) {
		this.active.set(false);
	}

	/**
	 * Reset Spring's common reflection metadata caches, in particular the
	 * {@link ReflectionUtils}, {@link AnnotationUtils}, {@link ResolvableType}
	 * and {@link CachedIntrospectionResults} caches.
	 * @since 4.2
	 * @see ReflectionUtils#clearCache()
	 * @see AnnotationUtils#clearCache()
	 * @see ResolvableType#clearCache()
	 * @see CachedIntrospectionResults#clearClassLoader(ClassLoader)
	 */
	protected void resetCommonCaches() {
		ReflectionUtils.clearCache();
		AnnotationUtils.clearCache();
		ResolvableType.clearCache();
		CachedIntrospectionResults.clearClassLoader(getClassLoader());
	}


	/**
	 * Register a shutdown hook {@linkplain Thread#getName() named}
	 * {@code SpringContextShutdownHook} with the JVM runtime, closing this
	 * context on JVM shutdown unless it has already been closed at that time.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * @see Runtime#addShutdownHook
	 * @see ConfigurableApplicationContext#SHUTDOWN_HOOK_THREAD_NAME
	 * @see #close()
	 * @see #doClose()
	 */
	@Override
	public void registerShutdownHook() {
		if (this.shutdownHook == null) {
			// No shutdown hook registered yet.
			this.shutdownHook = new Thread(SHUTDOWN_HOOK_THREAD_NAME) {
				@Override
				public void run() {
					synchronized (startupShutdownMonitor) {
						doClose();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
	}

	/**
	 * Callback for destruction of this instance, originally attached
	 * to a {@code DisposableBean} implementation (not anymore in 5.0).
	 * <p>The {@link #close()} method is the native way to shut down
	 * an ApplicationContext, which this method simply delegates to.
	 * @deprecated as of Spring Framework 5.0, in favor of {@link #close()}
	 */
	@Deprecated
	public void destroy() {
		close();
	}

	/**
	 * Close this application context, destroying all beans in its bean factory.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * Also removes a JVM shutdown hook, if registered, as it's not needed anymore.
	 * @see #doClose()
	 * @see #registerShutdownHook()
	 */
	@Override
	public void close() {
		synchronized (this.startupShutdownMonitor) {
			doClose();
			// If we registered a JVM shutdown hook, we don't need it anymore now:
			// We've already explicitly closed the context.
			if (this.shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				}
				catch (IllegalStateException ex) {
					// ignore - VM is already shutting down
				}
			}
		}
	}

	/**
	 * Actually performs context closing: publishes a ContextClosedEvent and
	 * destroys the singletons in the bean factory of this application context.
	 * <p>Called by both {@code close()} and a JVM shutdown hook, if any.
	 * @see org.springframework.context.event.ContextClosedEvent
	 * @see #destroyBeans()
	 * @see #close()
	 * @see #registerShutdownHook()
	 */
	protected void doClose() {
		// Check whether an actual close attempt is necessary...
		if (this.active.get() && this.closed.compareAndSet(false, true)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this);
			}

			LiveBeansView.unregisterApplicationContext(this);

			try {
				// Publish shutdown event.
				publishEvent(new ContextClosedEvent(this));
			}
			catch (Throwable ex) {
				logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
			}

			// Stop all Lifecycle beans, to avoid delays during individual destruction.
			if (this.lifecycleProcessor != null) {
				try {
					this.lifecycleProcessor.onClose();
				}
				catch (Throwable ex) {
					logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
				}
			}

			// Destroy all cached singletons in the context's BeanFactory.
			destroyBeans();

			// Close the state of this context itself.
			closeBeanFactory();

			// Let subclasses do some final clean-up if they wish...
			onClose();

			// Reset local application listeners to pre-refresh state.
			if (this.earlyApplicationListeners != null) {
				this.applicationListeners.clear();
				this.applicationListeners.addAll(this.earlyApplicationListeners);
			}

			// Switch to inactive.
			this.active.set(false);
		}
	}

	/**
	 * Template method for destroying all beans that this context manages.
	 * The default implementation destroy all cached singletons in this context,
	 * invoking {@code DisposableBean.destroy()} and/or the specified
	 * "destroy-method".
	 * <p>Can be overridden to add context-specific bean destruction steps
	 * right before or right after standard singleton destruction,
	 * while the context's BeanFactory is still active.
	 * @see #getBeanFactory()
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#destroySingletons()
	 */
	protected void destroyBeans() {
		getBeanFactory().destroySingletons();
	}

	/**
	 * Template method which can be overridden to add context-specific shutdown work.
	 * The default implementation is empty.
	 * <p>Called at the end of {@link #doClose}'s shutdown procedure, after
	 * this context's BeanFactory has been closed. If custom shutdown logic
	 * needs to execute while the BeanFactory is still active, override
	 * the {@link #destroyBeans()} method instead.
	 */
	protected void onClose() {
		// For subclasses: do nothing by default.
	}

	@Override
	public boolean isActive() {
		return this.active.get();
	}

	/**
	 * Assert that this context's BeanFactory is currently active,
	 * throwing an {@link IllegalStateException} if it isn't.
	 * <p>Invoked by all {@link BeanFactory} delegation methods that depend
	 * on an active context, i.e. in particular all bean accessor methods.
	 * <p>The default implementation checks the {@link #isActive() 'active'} status
	 * of this context overall. May be overridden for more specific checks, or for a
	 * no-op if {@link #getBeanFactory()} itself throws an exception in such a case.
	 */
	protected void assertBeanFactoryActive() {
		if (!this.active.get()) {
			if (this.closed.get()) {
				throw new IllegalStateException(getDisplayName() + " has been closed already");
			}
			else {
				throw new IllegalStateException(getDisplayName() + " has not been refreshed yet");
			}
		}
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, requiredType);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, args);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType);
	}

	@Override
	public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType, args);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isSingleton(name);
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isPrototype(name);
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name, allowFactoryBeanInit);
	}

	@Override
	public String[] getAliases(String name) {
		return getBeanFactory().getAliases(name);
	}


	//---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public boolean containsBeanDefinition(String beanName) {
		return getBeanFactory().containsBeanDefinition(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return getBeanFactory().getBeanDefinitionCount();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForAnnotation(annotationType);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansWithAnnotation(annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return getParent();
	}

	@Override
	public boolean containsLocalBean(String name) {
		return getBeanFactory().containsLocalBean(name);
	}

	/**
	 * Return the internal bean factory of the parent context if it implements
	 * ConfigurableApplicationContext; else, return the parent context itself.
	 * @see org.springframework.context.ConfigurableApplicationContext#getBeanFactory
	 */
	@Nullable
	protected BeanFactory getInternalParentBeanFactory() {
		/*
		* 如果是ConfigurableApplicationContext类型，则直接返回父类的Bean工厂
		* 如果不是直接返回父类
		* */
		return (getParent() instanceof ConfigurableApplicationContext ?
				((ConfigurableApplicationContext) getParent()).getBeanFactory() : getParent());
	}


	//---------------------------------------------------------------------
	// Implementation of MessageSource interface
	//---------------------------------------------------------------------

	@Override
	public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
		return getMessageSource().getMessage(code, args, defaultMessage, locale);
	}

	@Override
	public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(code, args, locale);
	}

	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(resolvable, locale);
	}

	/**
	 * Return the internal MessageSource used by the context.
	 * @return the internal MessageSource (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	private MessageSource getMessageSource() throws IllegalStateException {
		if (this.messageSource == null) {
			throw new IllegalStateException("MessageSource not initialized - " +
					"call 'refresh' before accessing messages via the context: " + this);
		}
		return this.messageSource;
	}

	/**
	 * Return the internal message source of the parent context if it is an
	 * AbstractApplicationContext too; else, return the parent context itself.
	 */
	@Nullable
	protected MessageSource getInternalParentMessageSource() {
		return (getParent() instanceof AbstractApplicationContext ?
				((AbstractApplicationContext) getParent()).messageSource : getParent());
	}


	//---------------------------------------------------------------------
	// Implementation of ResourcePatternResolver interface
	//---------------------------------------------------------------------

	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		return this.resourcePatternResolver.getResources(locationPattern);
	}


	//---------------------------------------------------------------------
	// Implementation of Lifecycle interface
	//---------------------------------------------------------------------

	@Override
	public void start() {
		getLifecycleProcessor().start();
		publishEvent(new ContextStartedEvent(this));
	}

	@Override
	public void stop() {
		getLifecycleProcessor().stop();
		publishEvent(new ContextStoppedEvent(this));
	}

	@Override
	public boolean isRunning() {
		return (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning());
	}


	//---------------------------------------------------------------------
	// Abstract methods that must be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * Subclasses must implement this method to perform the actual configuration load.
	 * The method is invoked by {@link #refresh()} before any other initialization work.
	 * <p>A subclass will either create a new bean factory and hold a reference to it,
	 * or return a single BeanFactory instance that it holds. In the latter case, it will
	 * usually throw an IllegalStateException if refreshing the context more than once.
	 * @throws BeansException if initialization of the bean factory failed
	 * @throws IllegalStateException if already initialized and multiple refresh
	 * attempts are not supported
	 */
	protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

	/**
	 * Subclasses must implement this method to release their internal bean factory.
	 * This method gets invoked by {@link #close()} after all other shutdown work.
	 * <p>Should never throw an exception but rather log shutdown failures.
	 */
	protected abstract void closeBeanFactory();

	/**
	 * Subclasses must return their internal bean factory here. They should implement the
	 * lookup efficiently, so that it can be called repeatedly without a performance penalty.
	 * <p>Note: Subclasses should check whether the context is still active before
	 * returning the internal bean factory. The internal factory should generally be
	 * considered unavailable once the context has been closed.
	 * @return this application context's internal bean factory (never {@code null})
	 * @throws IllegalStateException if the context does not hold an internal bean factory yet
	 * (usually if {@link #refresh()} has never been called) or if the context has been
	 * closed already
	 * @see #refreshBeanFactory()
	 * @see #closeBeanFactory()
	 */
	@Override
	public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;


	/**
	 * Return information about this context.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getDisplayName());
		sb.append(", started on ").append(new Date(getStartupDate()));
		ApplicationContext parent = getParent();
		if (parent != null) {
			sb.append(", parent: ").append(parent.getDisplayName());
		}
		return sb.toString();
	}

}
