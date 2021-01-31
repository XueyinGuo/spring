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

package org.springframework.beans.factory.support;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.AutowiredPropertyMarker;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.StringUtils;

/**
 * Abstract bean factory superclass that implements default bean creation,
 * with the full capabilities specified by the {@link RootBeanDefinition} class.
 * Implements the {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface in addition to AbstractBeanFactory's {@link #createBean} method.
 *
 * <p>Provides bean creation (with constructor resolution), property population,
 * wiring (including autowiring), and initialization. Handles runtime bean
 * references, resolves managed collections, calls initialization methods, etc.
 * Supports autowiring constructors, properties by name, and properties by type.
 *
 * <p>The main template method to be implemented by subclasses is
 * {@link #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)},
 * used for autowiring by type. In case of a factory which is capable of searching
 * its bean definitions, matching beans will typically be implemented through such
 * a search. For other factory styles, simplified matching algorithms can be implemented.
 *
 * <p>Note that this class does <i>not</i> assume or implement bean definition
 * registry capabilities. See {@link DefaultListableBeanFactory} for an implementation
 * of the {@link org.springframework.beans.factory.ListableBeanFactory} and
 * {@link BeanDefinitionRegistry} interfaces, which represent the API and SPI
 * view of such a factory, respectively.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Chris Beams
 * @author Sam Brannen
 * @author Phillip Webb
 * @since 13.02.2004
 * @see RootBeanDefinition
 * @see DefaultListableBeanFactory
 * @see BeanDefinitionRegistry
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	/** Strategy for creating bean instances. */
	private InstantiationStrategy instantiationStrategy = new CglibSubclassingInstantiationStrategy();

	/** Resolver strategy for method parameter names. */
	@Nullable
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	/** Whether to automatically try to resolve circular references between beans. */
	private boolean allowCircularReferences = true;

	/**
	 * Whether to resort to injecting a raw bean instance in case of circular reference,
	 * even if the injected bean eventually got wrapped.
	 */
	private boolean allowRawInjectionDespiteWrapping = false;

	/**
	 * Dependency types to ignore on dependency check and autowire, as Set of
	 * Class objects: for example, String. Default is none.
	 */
	private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

	/**
	 * Dependency interfaces to ignore on dependency check and autowire, as Set of
	 * Class objects. By default, only the BeanFactory interface is ignored.
	 */
	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	/**
	 * The name of the currently created bean, for implicit dependency registration
	 * on getBean etc invocations triggered from a user-specified Supplier callback.
	 */
	private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

	/** Cache of unfinished FactoryBean instances: FactoryBean name to BeanWrapper. */
	private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

	/** Cache of candidate factory methods per factory class. */
	private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();

	/** Cache of filtered PropertyDescriptors: bean Class to PropertyDescriptor array. */
	private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
			new ConcurrentHashMap<>();


	/**
	 * Create a new AbstractAutowireCapableBeanFactory.
	 */
	public AbstractAutowireCapableBeanFactory() {
		/*
		* super中设置 beanClassLoader 类加载器，用来处理bean的名称
		* CacheBeanMetadata 设置bean元数据的缓存（缓存干嘛先不管）
		* 剩下还有一堆的属性值设置，其中比较重要的是：
		* allowCircularReference = true（是否解决循环依赖的问题）
		* */
		super();
		/*
		* 还设置了一些忽略依赖的Aware接口
        * 【在bean创建过程中，我们会进行一系列的Aware接口的处理，叫做invokeAwareMethods用来统一处理】
        * 【Aware接口的一些基本操作】
        * 这里设置忽略是为了在bean创建过程中取统一处理，而不是在这里就进行处理，所以先设置了忽略
        * TODO 写和不写的小区别，请见下回分解
		* */
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
	}

	/**
	 * Create a new AbstractAutowireCapableBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 */
	public AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this();
		setParentBeanFactory(parentBeanFactory);
	}


	/**
	 * Set the instantiation strategy to use for creating bean instances.
	 * Default is CglibSubclassingInstantiationStrategy.
	 * @see CglibSubclassingInstantiationStrategy
	 */
	public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
		this.instantiationStrategy = instantiationStrategy;
	}

	/**
	 * Return the instantiation strategy to use for creating bean instances.
	 */
	protected InstantiationStrategy getInstantiationStrategy() {
		return this.instantiationStrategy;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed (e.g. for constructor names).
	 * <p>Default is a {@link DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * Return the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed.
	 */
	@Nullable
	protected ParameterNameDiscoverer getParameterNameDiscoverer() {
		return this.parameterNameDiscoverer;
	}

	/**
	 * Set whether to allow circular references between beans - and automatically
	 * try to resolve them.
	 * <p>Note that circular reference resolution means that one of the involved beans
	 * will receive a reference to another bean that is not fully initialized yet.
	 * This can lead to subtle and not-so-subtle side effects on initialization;
	 * it does work fine for many scenarios, though.
	 * <p>Default is "true". Turn this off to throw an exception when encountering
	 * a circular reference, disallowing them completely.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans. Refactor your application logic to have the two beans
	 * involved delegate to a third bean that encapsulates their common logic.
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	/**
	 * Set whether to allow the raw injection of a bean instance into some other
	 * bean's property, despite the injected bean eventually getting wrapped
	 * (for example, through AOP auto-proxying).
	 * <p>This will only be used as a last resort in case of a circular reference
	 * that cannot be resolved otherwise: essentially, preferring a raw instance
	 * getting injected over a failure of the entire bean wiring process.
	 * <p>Default is "false", as of Spring 2.0. Turn this on to allow for non-wrapped
	 * raw beans injected into some of your references, which was Spring 1.2's
	 * (arguably unclean) default behavior.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans, in particular with auto-proxying involved.
	 * @see #setAllowCircularReferences
	 */
	public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
		this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
	}

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 */
	public void ignoreDependencyType(Class<?> type) {
		this.ignoredDependencyTypes.add(type);
	}

	/**
	 * Ignore the given dependency interface for autowiring.
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	public void ignoreDependencyInterface(Class<?> ifc) {
		this.ignoredDependencyInterfaces.add(ifc);
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof AbstractAutowireCapableBeanFactory) {
			AbstractAutowireCapableBeanFactory otherAutowireFactory =
					(AbstractAutowireCapableBeanFactory) otherFactory;
			this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
			this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
			this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
			this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
		}
	}


	//-------------------------------------------------------------------------
	// Typical methods for creating and populating external bean instances
	//-------------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public <T> T createBean(Class<T> beanClass) throws BeansException {
		// Use prototype bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass);
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(beanClass, getBeanClassLoader());
		return (T) createBean(beanClass.getName(), bd, null);
	}

	@Override
	public void autowireBean(Object existingBean) {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), getBeanClassLoader());
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public Object configureBean(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition mbd = getMergedBeanDefinition(beanName);
		RootBeanDefinition bd = null;
		if (mbd instanceof RootBeanDefinition) {
			RootBeanDefinition rbd = (RootBeanDefinition) mbd;
			bd = (rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition());
		}
		if (bd == null) {
			bd = new RootBeanDefinition(mbd);
		}
		if (!bd.isPrototype()) {
			bd.setScope(SCOPE_PROTOTYPE);
			bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), getBeanClassLoader());
		}
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(beanName, bd, bw);
		return initializeBean(beanName, existingBean, bd);
	}


	//-------------------------------------------------------------------------
	// Specialized methods for fine-grained control over the bean lifecycle
	//-------------------------------------------------------------------------

	@Override
	public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		return createBean(beanClass.getName(), bd, null);
	}

	@Override
	public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		if (bd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR) {
			return autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
		}
		else {
			Object bean;
			if (System.getSecurityManager() != null) {
				bean = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(bd, null, this),
						getAccessControlContext());
			}
			else {
				bean = getInstantiationStrategy().instantiate(bd, null, this);
			}
			populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
			return bean;
		}
	}

	@Override
	public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException {

		if (autowireMode == AUTOWIRE_CONSTRUCTOR) {
			throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
		}
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd =
				new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition bd = getMergedBeanDefinition(beanName);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
	}

	@Override
	public Object initializeBean(Object existingBean, String beanName) {
		return initializeBean(beanName, existingBean, null);
	}

	@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			Object current = processor.postProcessBeforeInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			/*
			* 配置了事务的话会在这里创建事务对象
			* */// BeanPostProcessor可能有多个，BFPP也可能有多个
			Object current = processor.postProcessAfterInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public void destroyBean(Object existingBean) {
		new DisposableBeanAdapter(existingBean, getBeanPostProcessors(), getAccessControlContext()).destroy();
	}


	//-------------------------------------------------------------------------
	// Delegate methods for resolving injection points
	//-------------------------------------------------------------------------

	@Override
	public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
			return getBean(name, descriptor.getDependencyType());
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException {
		return resolveDependency(descriptor, requestingBeanName, null, null);
	}


	//---------------------------------------------------------------------
	// Implementation of relevant AbstractBeanFactory template methods
	//---------------------------------------------------------------------

	/**
	 * Central method of this class: creates a bean instance,
	 * populates the bean instance, applies post-processors, etc.
	 * @see #doCreateBean
	 */
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}
		RootBeanDefinition mbdToUse = mbd;

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition.
		/*
		* 根据 mbd 中的 字符串类型的className 最终调用完 Class.forName() 来确定resolvedClass 对象的值，
		* 有了 Class对象可以直接利用反射 newInstance 了，但是Spring没有直接这么干
		* */
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}

		// Prepare method overrides.
		/*
		* 验证及准备覆盖的方法，lookup-method   replace-method
		* Spring 中默认对象都是单例的， Spring会在一级缓存中持有该对象，方便下次直接获取
		* 如果对象是在原型作用域，则Spring不会创建缓存该对象，而每次都要创建新的对象
		* 如果想在一个单例模式的Bean中 引用一个原型模式的Bean， 怎么办？
		*
		* 这种情况下，就需要用lookup-method 标签来解决这个问题
		*
		* 当需要的bean对象中包含 lookup-method   replace-method 标签的时候，会产生覆盖操作，
		* 设置一个标志位为 false
		* */
		try {
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		try {
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			/*
			* 给BeanPostProcessor一个机会返回一个代理来替代真正的实例
			* 如果代理的bean不为空，则直接返回代理bean
			* 往下的 doCreateBean 也不用执行了
			*
			* 是否执行doCreateBean取决于 之前定义的BeanPostProcessor中是否包含了提前创建Bean对象的BeanPostProcessor，
			* 如果包含就提前创建，如果不包含才继续往下执行 doCreateBean
			* */
			/*
			* 【【【AOP 的时候需要看的注释】】】
			*   shouldSkip() 方法中 获取所有的 Advisors
			*
			*		 findCandidateAdvisors() 创建bean
			*
			*		 创建bean,并添加到 List 中，待返回
			*
			*		 此处创建对象有意思的地方在于：之前创建一个半成品对象，是直接反射调用无参构造，然后调用Set方法去给属性赋值，所谓的实例化初始化时分开的
			*		 但是，在这些Advisor类中没有无参构造，只有一个有参构造，所以在这里创建 Advisor对象的时候，我必须先把有参构造方法中
			*		 	要求的参数先创建好。所以这里的对象的创造，是需要很多层的嵌套的（跨方法递归的）。
			*
			*		        		          		   		 { -----> MethodLocatingFactoryBean
			*			Advisor#0--#4 --->   adviceDef --->  { -----> expression="execution(Integer com.szu.spring.aopTest.MyCalculator.*(Integer,Integer))"
			*						                 	     { -----> SimpleBeanFactoryAwareAspectInstanceFactory
			*
			*		 意思就是说在创建 Advisor 的时候， 三个嵌套对象也要创建好
			* */
			/*
			 * 【【【AOP 的时候需要看的注释】】】即便是普通对象也返回空，我们要记得开始这个逻辑的入口，“给BPP一个机会返回代理对象”，
			 * 其实主要针对的事用户自定义的Bean before Instantiation 中返回代理对象
			 * 如果开启了 AOP ，那是一个标准化的代理创建流程，而这里更侧重于用户自定的方式返回代理，所以此时也是返回空。  【【【即使一个对象需要被代理，那么这个对象也要被创建，不过创建完是要被替换的】】】
			 *  */
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		try {
			/*
			* 实际创建bean
			* ！！！！！！！！！！！！！！！！！！！！！！！！
			* */
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			// A previously detected exception with proper bean creation context already,
			// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}

	/**
	 * Actually create the specified bean. Pre-creation processing has already happened
	 * at this point, e.g. checking {@code postProcessBeforeInstantiation} callbacks.
	 * <p>Differentiates between default bean instantiation, use of a
	 * factory method, and autowiring a constructor.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 * @see #instantiateBean
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 */
	protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		// Instantiate the bean.
		/*
		* beanWrapper 用来持有创建出来的 bean对象
		* BeanWrapper 继承了 PropertyEditorRegistry, TypeConverter 接口
		* 可以直接做属性值的类型转换操作
		* */
		BeanWrapper instanceWrapper = null;
		if (mbd.isSingleton()) {
			/* 如果是单例对象，从factoryBean实例缓存中移除当前的bean定义信息 */
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
			/* 执行bean对应的策略创建新的实例，比如工厂方法，构造函数主动注入或者简单初始化 */
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		Object bean = instanceWrapper.getWrappedInstance();
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			mbd.resolvedTargetType = beanType;
		}

		// Allow post-processors to modify the merged bean definition.
		/*
		* @PreDestroy  @PostConstruct 注解，Bean的 LifeCycle（生命周期）
		* 也就是Bean的Init函数和Destroy函数
		* ============================================================================================================================
		* ============================================================================================================================
		* 											BeanPostProcessor 接口 有几个子类 ：
		*
		* InstantiationAwareBeanPostProcessor              DestructionAwareBeanPostProcessor         MergedBeanDefinitionPostProcessor
		*   (实例化前后、初始化前后、值处理)
		* 	给个机会返回代理Bean的时候用的
		*             |
		* SmartInstantiationAwareBeanPostProcessor
		*  （预测类型、决定构造器、处理循环依赖）
		* ============================================================================================================================
   		* ============================================================================================================================
		*
		* MergedBeanDefinitionPostProcessor 接口中 都是操作 BeanDefinition 的方法，
		* 在它的实现类中都有相同的描述：Annotation 注解 （AnnotationBeanPostProcessor）
		* 重要的实现类有 CommonAnnotationBeanPostProcessor 和 它的父类 InitDestroyAnnotationBeanPostProcessor
		*
		*
		* */
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					/*
					* MergedBeanDefinitionPostProcessor 后置处理器修改合并 Bean定义信息
					*
					* @PostConstruct  @PreDestroy  @Resource 注解的处理
					* 并把找到的方法们加入到对应的BeanDefinition的某个属性值中，TODO 等待填充完属性和执行完所有的before after init 等方法之后调用？
					* */
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				mbd.postProcessed = true;
			}
		}

		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		/*
		 * 解决循环依赖问题！
		 * 提前暴露！
		 * 为啥我们要把对象实例化和初始化分清，因为实例化只是为当前对象的完成了创建工作，值也都是默认值，
		 * 当初始化的时候才进行赋值操作。
		 * 但是循环依赖不会在实例化时发生。
		 *
		 * 赋值的时候，值的类型可能是引用类型，如果引用对象已经创建，就可以直接从容器中获取，如果没有创建，就还需要中断去创建另外的对象，
		 * 但是如果依赖的对象还在依赖当前对象，当前对象没有创建，就产生了循环依赖问题
		 *
		 * 如何解决循环依赖问题呢？
		 * 因为实例化和初始化时分开的，当完成实例化之后就可以让其他对象引用当前对象，只不过这个对象暂时还是一个没有任何属性值填充而已，
		 * 后续仍需要对此对象进行剩余的操作。其实就是获取半成品的对象地址，保证对象能被找到，而对象在对空间中是否设置属性值，完全可以在之后进行操作
		 *
		 *
		 * ==================================================================================================================================
		 * ==================================================================================================================================
		 * ==================================================================================================================================
		 * 获取完成对象的时候分为两个步骤，实例化和初始化！ 初始化环节的填充属性不会改变对象的地址空间
		 * 但是我们需要考虑的事：Spring在使用时会配置AOP，需要生成具体的代理对象，
		 *
		 * 1.然而在生成代理对象之前需要先生成普通对象。（因为就算是配置了AOP，AOP也是一个Bean，也需要走完上述的createBeanInstance那个方法流程）
		 * 2.所以我们无法确定什么时候获取哪个具体的对象（包括代理对象和普通对象）
		 * 3.而且在Spring中有可能同时存在一个BeanName对应普通对象和代理对象两个的情况，当用beanName获取Bean时当然不能两个对象全部获取到，
		 * 		所以可以考虑使用分类存储。即：把普通对象和代理对象分别缓存起来。
		 * 4.那这两个缓存我优先获取哪个呢？ 其实可以只获取代理对象，因为代理对象包含了普通对象的所有功能，如果一个对象需要被代理
		 * 		那么此时普通对象就可以不存在了，只需要代理对象就可以了
		 * 		既然代理包含了普通对象所有的功能，在后续创建了代理对象之后，普通对象可以直接被覆盖掉，普通对象存在的必要都没有了！！！
		 *
		 * 回到 2 中，什么时候获取对象呢？
		 * 我们在获取对象的时候，如果检测到当前对象需要被代理，那么直接创建代理对象对其进行覆盖
		 * 那么我怎么又能够在随时需要的时候创建代理对象呢？
		 * 我们可以传递一个生成代理对象的匿名内部类，当需要获取代理对象的时候，直接调用匿名内部类（现在匿名内部类的主要表达方式变成了Lambda表达式）来生成
		 * 		代理对象，类似于回调机制！！！！！！
		 *
		 *
		 * ------------------------------------------------------------------------------------------------------------------------
		 * ------------------------------------------------------------------------------------------------------------------------
		 * 一二三级缓存中大体内容一览：
		 *
		 * 一级缓存 singletonObjects ---------> 保存成品对象（普通对象 + 代理对象），方便之后直接获取                key：BeanName -> value：Object
		 * 二级缓存 earlySingletonObjects ----> 保存半成品对象（刚刚进行实例化但还没初始化的对象），方便解决循环依赖    key：BeanName -> value：Object
		 * 三级缓存 singletonFactories -------> 保存生成代理对象的 lambda 表达式                                key：BeanName -> value：lambda
		 *
		 * 所以二级缓存解决了循环依赖，它让对象得到了提前暴露 ！！！
		 * 三个缓存的对象查找顺序就是从 一级缓存 -> 二级缓存 -> 三级缓存
		 * 那么三级缓存 可以 不经过 二级缓存 直接到 一级缓存吗？
		 * 答案当然是肯定的！
		 *
		 * 其实二级缓存来解决循环依赖问题已经完全足够了，三级缓存的意义就在于代理对象创建，如果没有代理对象，二级缓存足以解决问题
		 * ------------------------------------------------------------------------------------------------------------------------
		 * ------------------------------------------------------------------------------------------------------------------------
		 *
		 * 5.初始化包含哪些环节：
		 * 		a:填充属性
		 * 		b:执行Aware接口对应的方法
		 * 		c:执行BeanPostProcessor的before方法
		 * 		d:执行init-method
		 * 		e:执行BeanPostProcessor的after方法
		 * 上述步骤完成之后为了获取到一个完整的成品对象，但是在初始化之前我们能确定哪一个对象需要生成代理对象吗？
		 * 答案是否定的。但是三级缓存只是一个回调机制，我们可以把所有bean的创建代理对象的lambda表达式放在里边，
		 * 在后续如果需要的时候直接调用三级缓存中的 lambda。如果不需要之后可以把三级缓存中的lambda清除掉，
		 * 清除的时机是在往一级缓存中放成品对象的时候！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
		 *
		 * 6.我在什么时候需要生成具体的代理对象呢？
		 *
		 * 		a: 在进行属性注入的时候，获取该对象的引用对象时候检测注入的引用属性是否需要被代理，如果需要直接创建代理对象
		 * 				比如：A有B对象的引用，给A对象进行属性注入的时候，发现B对象需要被代理，此时就可以直接创建B对象的代理对象
		 *
		 * 		b: 在整个过程中，没有其他对象依赖当前对象，那么可以在最终生成完整对象之前生成代理对象即可，
		 * 		   其实就是可以在执行BeanPostProcessor的after方法 时生成代理对象！
		 * ==================================================================================================================================
 		 * ==================================================================================================================================
 		 * ==================================================================================================================================
		 *
		 * 所以此处要分为两种情况：构造器 和 set方式
		 * 如果是构造器方式创建对象的时候就已经产生了循环依赖，我们没有办法解决。
		 * 如果是set方式就可以解决循环依赖问题，因为我们把对象创建和赋值分开了
		 *
		 * 提前暴露 ！！！ 就是 只完成了实例化 还没完成初始化，后续的时候在为其补充对应的属性值
		 * */

		/* 判断当前 bean 是否需要提前暴露：单例 && 允许循环依赖 && 当前 Bean正在创建过程中，  检测循环依赖 */
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			/*
			 * 把调用时的匿名内部类（lambda表达式）放入三级缓存
			 * */
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}

		// Initialize the bean instance.
		Object exposedObject = bean;
		try {
			/*
			* 对象初始化分为两个步骤：填充属性 和 执行init-method，这个方法完成了属性填充
			* */
			/*
			* ==========================================================================================================
			* ==========================================================================================================
			* ==========================================================================================================
			* 属性填充时候需要注意的地方有：
			* 对象有两种类型，
			* 1.基本数据类型
			* 		byte short int long double float boolean String（虽然是引用类型，但是可以直接完成赋值操作）
			* 		直接完成赋值操作
			* 2.引用类型
			* 		赋值的时候从容器中查找，如果有直接赋值，如果没有就创建。
			* 		但是引用类型会有另外一个问题：找到多个合适的对象怎么办？（比如定义两个Bean同类的标签，那么就有了多个可以查找到的对象）
			* 		所以这时候我们应该考虑到注入的方式
			* 			a:不注入        <bean id="" class="" autowire="no">
			* 			b:按照类型注入   <bean id="" class="" autowire="byType">
			* 			c:按照名称注入   <bean id="" class="" autowire="byName">
			* 			d:按照构造器注入 <bean id="" class="" autowire="constructor">
			*
			* 除了基本数据类型和普通引用类型之外，还有其他的引用类型：数组、List、Map、Set、Properties，
			* 这些数据结构中既可以放置基本数据类型也可以放置其他的普通引用类型
			*
			* 考虑到字符串和其他数据类型的特殊关系，假如我们在XML中定义一个 <bean id="" class="" name="1">的标签
			* 那么name这个“1”到底应该作为 int 还是作为 String 呢？虽然可以指定类型，但是我们如果类型也不指定的话，那就需要考虑另外的一个问题
			* 也就是类型转换！！！！！！
			*
			*								属性填充的时候如果有依赖的《Bean》也会顺便一块创建，比如往List、Map、Set等中设置Bean时！！
			*
			* 								字符串解析成Address类型的过程
			*  								1.在此进行值转换 比如 star中有个address属性是Address类型，自己注册了自定义编辑器传入一个字符串解析成Address类型
			*								2.把解析到的值存入到每个 PropertyValue 的 convertedValue 属性中
			*								3.从刚才的PropertyValue存入的convertedValue中 获取相应的属性值，并对象相应的属性赋值
			* ==========================================================================================================
			* ==========================================================================================================
			* ==========================================================================================================
			* */
 			populateBean(beanName, mbd, instanceWrapper);
			/*
			 * 如果bean实现了Aware接口的话，在此处
			 * 执行BeanPostProcessorBefore
			 * 执行init-method
			 * 执行 postProcessAfterInitialization
			 * */
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			}
			else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}

		/*
		* 检测到有对象依赖当前对象
		* */
		if (earlySingletonExposure) {
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		// Register bean as disposable.
		/*
		* 容器关闭的时候需要销毁对象
		* 这个方法中埋了一些钩子函数来进行销毁对象
		* */
		try {
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		return exposedObject;
	}

	@Override
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = determineTargetType(beanName, mbd, typesToMatch);
		// Apply SmartInstantiationAwareBeanPostProcessors to predict the
		// eventual type after a before-instantiation shortcut.
		if (targetType != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			boolean matchingOnlyFactoryBean = typesToMatch.length == 1 && typesToMatch[0] == FactoryBean.class;
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					Class<?> predicted = ibp.predictBeanType(targetType, beanName);
					if (predicted != null &&
							(!matchingOnlyFactoryBean || FactoryBean.class.isAssignableFrom(predicted))) {
						return predicted;
					}
				}
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 */
	@Nullable
	protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType == null) {
			targetType = (mbd.getFactoryMethodName() != null ?
					getTypeForFactoryMethod(beanName, mbd, typesToMatch) :
					resolveBeanClass(mbd, beanName, typesToMatch));
			if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
				mbd.resolvedTargetType = targetType;
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition which is based on
	 * a factory method. Only called if there is no singleton instance registered
	 * for the target bean already.
	 * <p>This implementation determines the type matching {@link #createBean}'s
	 * different creation strategies. As far as possible, we'll perform static
	 * type checking to avoid creation of the target bean.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see #createBean
	 */
	@Nullable
	protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
		if (cachedReturnType != null) {
			return cachedReturnType.resolve();
		}

		Class<?> commonType = null;
		Method uniqueCandidate = mbd.factoryMethodToIntrospect;

		if (uniqueCandidate == null) {
			Class<?> factoryClass;
			boolean isStatic = true;

			String factoryBeanName = mbd.getFactoryBeanName();
			if (factoryBeanName != null) {
				if (factoryBeanName.equals(beanName)) {
					throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
							"factory-bean reference points back to the same bean definition");
				}
				// Check declared factory method return type on factory class.
				factoryClass = getType(factoryBeanName);
				isStatic = false;
			}
			else {
				// Check declared factory method return type on bean class.
				factoryClass = resolveBeanClass(mbd, beanName, typesToMatch);
			}

			if (factoryClass == null) {
				return null;
			}
			factoryClass = ClassUtils.getUserClass(factoryClass);

			// If all factory methods have the same return type, return that type.
			// Can't clearly figure out exact method due to type converting / autowiring!
			int minNrOfArgs =
					(mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0);
			Method[] candidates = this.factoryMethodCandidateCache.computeIfAbsent(factoryClass,
					clazz -> ReflectionUtils.getUniqueDeclaredMethods(clazz, ReflectionUtils.USER_DECLARED_METHODS));

			for (Method candidate : candidates) {
				if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate) &&
						candidate.getParameterCount() >= minNrOfArgs) {
					// Declared type variables to inspect?
					if (candidate.getTypeParameters().length > 0) {
						try {
							// Fully resolve parameter names and argument values.
							Class<?>[] paramTypes = candidate.getParameterTypes();
							String[] paramNames = null;
							ParameterNameDiscoverer pnd = getParameterNameDiscoverer();
							if (pnd != null) {
								paramNames = pnd.getParameterNames(candidate);
							}
							ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
							Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
							Object[] args = new Object[paramTypes.length];
							for (int i = 0; i < args.length; i++) {
								ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
										i, paramTypes[i], (paramNames != null ? paramNames[i] : null), usedValueHolders);
								if (valueHolder == null) {
									valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
								}
								if (valueHolder != null) {
									args[i] = valueHolder.getValue();
									usedValueHolders.add(valueHolder);
								}
							}
							Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(
									candidate, args, getBeanClassLoader());
							uniqueCandidate = (commonType == null && returnType == candidate.getReturnType() ?
									candidate : null);
							commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
							if (commonType == null) {
								// Ambiguous return types found: return null to indicate "not determinable".
								return null;
							}
						}
						catch (Throwable ex) {
							if (logger.isDebugEnabled()) {
								logger.debug("Failed to resolve generic return type for factory method: " + ex);
							}
						}
					}
					else {
						uniqueCandidate = (commonType == null ? candidate : null);
						commonType = ClassUtils.determineCommonAncestor(candidate.getReturnType(), commonType);
						if (commonType == null) {
							// Ambiguous return types found: return null to indicate "not determinable".
							return null;
						}
					}
				}
			}

			mbd.factoryMethodToIntrospect = uniqueCandidate;
			if (commonType == null) {
				return null;
			}
		}

		// Common return type found: all factory methods return same type. For a non-parameterized
		// unique candidate, cache the full type declaration context of the target factory method.
		cachedReturnType = (uniqueCandidate != null ?
				ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType));
		mbd.factoryMethodReturnType = cachedReturnType;
		return cachedReturnType.resolve();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, and {@code allowInit} is {@code true} a
	 * full creation of the FactoryBean is used as fallback (through delegation to the
	 * superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		// Check if the bean definition itself has defined the type with an attribute
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		ResolvableType beanType =
				(mbd.hasBeanClass() ? ResolvableType.forClass(mbd.getBeanClass()) : ResolvableType.NONE);

		// For instance supplied beans try the target type and bean class
		if (mbd.getInstanceSupplier() != null) {
			result = getFactoryBeanGeneric(mbd.targetType);
			if (result.resolve() != null) {
				return result;
			}
			result = getFactoryBeanGeneric(beanType);
			if (result.resolve() != null) {
				return result;
			}
		}

		// Consider factory methods
		String factoryBeanName = mbd.getFactoryBeanName();
		String factoryMethodName = mbd.getFactoryMethodName();

		// Scan the factory bean methods
		if (factoryBeanName != null) {
			if (factoryMethodName != null) {
				// Try to obtain the FactoryBean's object type from its factory method
				// declaration without instantiating the containing bean at all.
				BeanDefinition factoryBeanDefinition = getBeanDefinition(factoryBeanName);
				Class<?> factoryBeanClass;
				if (factoryBeanDefinition instanceof AbstractBeanDefinition &&
						((AbstractBeanDefinition) factoryBeanDefinition).hasBeanClass()) {
					factoryBeanClass = ((AbstractBeanDefinition) factoryBeanDefinition).getBeanClass();
				}
				else {
					RootBeanDefinition fbmbd = getMergedBeanDefinition(factoryBeanName, factoryBeanDefinition);
					factoryBeanClass = determineTargetType(factoryBeanName, fbmbd);
				}
				if (factoryBeanClass != null) {
					result = getTypeForFactoryBeanFromMethod(factoryBeanClass, factoryMethodName);
					if (result.resolve() != null) {
						return result;
					}
				}
			}
			// If not resolvable above and the referenced factory bean doesn't exist yet,
			// exit here - we don't want to force the creation of another bean just to
			// obtain a FactoryBean's object type...
			if (!isBeanEligibleForMetadataCaching(factoryBeanName)) {
				return ResolvableType.NONE;
			}
		}

		// If we're allowed, we can create the factory bean and call getObjectType() early
		if (allowInit) {
			FactoryBean<?> factoryBean = (mbd.isSingleton() ?
					getSingletonFactoryBeanForTypeCheck(beanName, mbd) :
					getNonSingletonFactoryBeanForTypeCheck(beanName, mbd));
			if (factoryBean != null) {
				// Try to obtain the FactoryBean's object type from this early stage of the instance.
				Class<?> type = getTypeForFactoryBean(factoryBean);
				if (type != null) {
					return ResolvableType.forClass(type);
				}
				// No type found for shortcut FactoryBean instance:
				// fall back to full creation of the FactoryBean instance.
				return super.getTypeForFactoryBean(beanName, mbd, true);
			}
		}

		if (factoryBeanName == null && mbd.hasBeanClass() && factoryMethodName != null) {
			// No early bean instantiation possible: determine FactoryBean's type from
			// static factory method signature or from class inheritance hierarchy...
			return getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
		}
		result = getFactoryBeanGeneric(beanType);
		if (result.resolve() != null) {
			return result;
		}
		return ResolvableType.NONE;
	}

	private ResolvableType getFactoryBeanGeneric(@Nullable ResolvableType type) {
		if (type == null) {
			return ResolvableType.NONE;
		}
		return type.as(FactoryBean.class).getGeneric();
	}

	/**
	 * Introspect the factory method signatures on the given bean class,
	 * trying to find a common {@code FactoryBean} object type declared there.
	 * @param beanClass the bean class to find the factory method on
	 * @param factoryMethodName the name of the factory method
	 * @return the common {@code FactoryBean} object type, or {@code null} if none
	 */
	private ResolvableType getTypeForFactoryBeanFromMethod(Class<?> beanClass, String factoryMethodName) {
		// CGLIB subclass methods hide generic parameters; look at the original user class.
		Class<?> factoryBeanClass = ClassUtils.getUserClass(beanClass);
		FactoryBeanMethodTypeFinder finder = new FactoryBeanMethodTypeFinder(factoryMethodName);
		ReflectionUtils.doWithMethods(factoryBeanClass, finder, ReflectionUtils.USER_DECLARED_METHODS);
		return finder.getResult();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, a full creation of the FactoryBean is
	 * used as fallback (through delegation to the superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	@Deprecated
	@Nullable
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * Obtain a reference for early access to the specified bean,
	 * typically for the purpose of resolving a circular reference.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param bean the raw bean instance
	 * @return the object to expose as bean reference
	 */
	protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
		Object exposedObject = bean;
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			/*
			* BeanPostProcessor中实现了AOP，所以
			* 当对象被代理的时候必须要用三级缓存来实现了
			* */
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					exposedObject = ibp.getEarlyBeanReference(exposedObject, beanName);
				}
			}
		}
		return exposedObject;
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Obtain a "shortcut" singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		synchronized (getSingletonMutex()) {
			BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
			if (bw != null) {
				return (FactoryBean<?>) bw.getWrappedInstance();
			}
			Object beanInstance = getSingleton(beanName, false);
			if (beanInstance instanceof FactoryBean) {
				return (FactoryBean<?>) beanInstance;
			}
			if (isSingletonCurrentlyInCreation(beanName) ||
					(mbd.getFactoryBeanName() != null && isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
				return null;
			}

			Object instance;
			try {
				// Mark this bean as currently in creation, even if just partially.
				beforeSingletonCreation(beanName);
				// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
				instance = resolveBeforeInstantiation(beanName, mbd);
				if (instance == null) {
					bw = createBeanInstance(beanName, mbd, null);
					instance = bw.getWrappedInstance();
				}
			}
			catch (UnsatisfiedDependencyException ex) {
				// Don't swallow, probably misconfiguration...
				throw ex;
			}
			catch (BeanCreationException ex) {
				// Instantiation failure, maybe too early...
				if (logger.isDebugEnabled()) {
					logger.debug("Bean creation exception on singleton FactoryBean type check: " + ex);
				}
				onSuppressedException(ex);
				return null;
			}
			finally {
				// Finished partial creation of this bean.
				afterSingletonCreation(beanName);
			}

			FactoryBean<?> fb = getFactoryBean(beanName, instance);
			if (bw != null) {
				this.factoryBeanInstanceCache.put(beanName, bw);
			}
			return fb;
		}
	}

	/**
	 * Obtain a "shortcut" non-singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		if (isPrototypeCurrentlyInCreation(beanName)) {
			return null;
		}

		Object instance;
		try {
			// Mark this bean as currently in creation, even if just partially.
			beforePrototypeCreation(beanName);
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			instance = resolveBeforeInstantiation(beanName, mbd);
			if (instance == null) {
				BeanWrapper bw = createBeanInstance(beanName, mbd, null);
				instance = bw.getWrappedInstance();
			}
		}
		catch (UnsatisfiedDependencyException ex) {
			// Don't swallow, probably misconfiguration...
			throw ex;
		}
		catch (BeanCreationException ex) {
			// Instantiation failure, maybe too early...
			if (logger.isDebugEnabled()) {
				logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
			}
			onSuppressedException(ex);
			return null;
		}
		finally {
			// Finished partial creation of this bean.
			afterPrototypeCreation(beanName);
		}

		return getFactoryBean(beanName, instance);
	}

	/**
	 * Apply MergedBeanDefinitionPostProcessors to the specified bean definition,
	 * invoking their {@code postProcessMergedBeanDefinition} methods.
	 * @param mbd the merged bean definition for the bean
	 * @param beanType the actual type of the managed bean instance
	 * @param beanName the name of the bean
	 * @see MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition
	 */
	protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof MergedBeanDefinitionPostProcessor) {
				MergedBeanDefinitionPostProcessor bdp = (MergedBeanDefinitionPostProcessor) bp; /* internalCommonAnnotationProcessor 是这个类对应BeanName */
				bdp.postProcessMergedBeanDefinition(mbd, beanType, beanName);  /* CommonAnnotationBeanPostProcessor 中处理 @PreDestroy @PostConstruct @Resource */
			}
		}
	}

	/**
	 * Apply before-instantiation post-processors, resolving whether there is a
	 * before-instantiation shortcut for the specified bean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the shortcut-determined bean instance, or {@code null} if none
	 */
	@Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			// Make sure bean class is actually resolved at this point.
			/*
			* ==================================================================================================
			* ==================================================================================================
			* BeanPostProcessor 接口 中的方法  postProcessBeforeInitialization()  postProcessAfterInitialization()
			* bean ==初始化== 的before和after的PostProcessor
			*
			* InstantiationAwareBeanPostProcessor 接口继承了 BeanPostProcessor，
			* 里边新多了一些方法： postProcessBeforeInstantiation()  postProcessAfterInstantiation()
			* bean ==实例化== 的before和after的PostProcessor
			*
			* 但是即使我们自己自定义了 MyInstantiationAwareBeanPostProcessor 实现了 InstantiationAwareBeanPostProcessor接口
			* 在注册BeanPostProcessor 时，也就是refresh()方法中的registerBeanPostProcessors(beanFactory);中进行getBean操作时
			*  hasInstantiationAwareBeanPostProcessors()判断仍是false，等这个Bean创建完了，创建之后的Bean的时候这个判断才是true。
			* 所以我们自己创建的 BeanPostProcessor 并不会创建代理对象，而是严格按照 doCreateBean 的流程走完
			*
			* 	【上边这句话就是的意思就是创建 MyInstantiationAwareBeanPostProcessor 这个Bean的时候，因为容器中现在没有 这个类型的 BPP】
			* 	【所以 hasInstantiationAwareBeanPostProcessors()仍然是 false， 等这个Bean创建完了，判断就是true了】
			* ==================================================================================================
			* ==================================================================================================
			*
			* mbd.isSynthetic(): 表示是否是应用程序本身定义的而不是用户定义的，创建aop的时候值为True，默认值是false，
			*
			* TODO hasInstantiationAwareBeanPostProcessors()为什么在 myInstantiationAwareBeanPostProcessor 的时候判断为False
			* */
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
					/*
					* 在 before 中可以使用代理的方式创建Bean，然后创建出来之后再从 after 中处理刚创建出来的Bean
					* */
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}

	/**
	 * Apply InstantiationAwareBeanPostProcessors to the specified bean definition
	 * (by class and name), invoking their {@code postProcessBeforeInstantiation} methods.
	 * <p>Any returned object will be used as the bean instead of actually instantiating
	 * the target bean. A {@code null} return value from the post-processor will
	 * result in the target bean being instantiated.
	 * @param beanClass the class of the bean to be instantiated
	 * @param beanName the name of the bean
	 * @return the bean object to use instead of a default instance of the target bean, or {@code null}
	 * @see InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
	 */
	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
				/*
				* ======================================================
				* ======================================================
				* 在创建第一个Bean对象之前需要吧所有的 AOP 相关的Bean全部创建完毕 ，
				* 所以在创建第一个Bean的时候，这里find找到所有的Advisor然后先实例化
				* ======================================================
				* */
				Object result = ibp.postProcessBeforeInstantiation(beanClass, beanName);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	/**
	 * Create a new instance for the specified bean, using an appropriate instantiation strategy:
	 * factory method, constructor autowiring, or simple instantiation.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a BeanWrapper for the new instance
	 * @see #obtainFromSupplier
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 * @see #instantiateBean
	 */
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// Make sure bean class is actually resolved at this point.
		/* 确认需要创建的bean实例的类可以实例化 */
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}
		/* 判断当前的beanDefinition中是否包含实例供应器，此处相当于一个回调方法，利用回调来创建bean
		* 创建对象的五种办法之一！！！！！！！！！！！！！！！！
		* 之前在做BeanDefinition解析的时候早就已经把工厂方法或者 Supplier 方法存在 BeanDefinition中，在创建当前Bean时，直接调用就好了
		* */
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			return obtainFromSupplier(instanceSupplier, beanName);
		}
		/* 如果工厂方法不为空，则使用工厂方法初始化策略
		*  创建对象的五种办法之一！！！！！！！！！！！！！！！！
		* 之前在做BeanDefinition解析的时候早就已经把工厂方法或者 Supplier 方法存在 BeanDefinition中，在创建当前Bean时，直接调用就好了
		*  */
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}
		// Shortcut when re-creating the same bean...
		/*
		* 一个类可能有多个构造器或者多个工厂方法，Spring根据参数个数、类型来确定需要调用的构造器或者哪个工厂方法，
		* 使用构造器创建完实例之后，Spring会把解析过后确定下来的构造器或者工厂方法保存在缓存中，避免再次创建相同的bean时再次解析
		*
		* 比如 Person p1 =  ac.getBean(Person.class);
		*     Person p2 =  ac.getBean(Person.class);
		* p2就不用再次解析一遍了，只解析第一次就够了
		*
		* mbd.resolvedConstructorOrFactoryMethod 会在第一次解析的时候做类似于缓存操作
		* */
		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		if (resolved) {
			if (autowireNecessary) {
				/*构造函数自动注入*/
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				/*使用默认构造器*/
				return instantiateBean(beanName, mbd);
			}
		}

		// Candidate constructors for autowiring?  构造方法们
		/*
		* Spring根据参数个数、类型来确定需要调用的构造器
		* 从beanPostProcessor中为自动装配寻找构造方法，有且仅有一个有参构造或者有且仅有@Autowired注解构造时， ctors不为空
		* */
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
		/*
		* 以下情况有一条就可以进入：
		* 1.存在可选构造方法
		* 2.自动装配模式为 构造函数自动装配
		* 3.给BeanDefinition中设置了构造参数值
		* 4.有参与构造函数列表的参数
		*   mbd.hasConstructorArgumentValues() 获取的就是 XML配置文件中如果使用构造器注入的话，就会获取到注入的属性
		*			======AOP相关：创建 Advisor 对象的时候，由于Advisor需要一个Advice对象作为参数，而且Advice需要三个参数（method，表达式，工厂）都会使用这个方法
		* */
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// Preferred constructors for default construction?
		/*
		* 找出最合适的构造方法，
		* 子类重写的方法中找到 @Primary 注解标识的方法
		* */
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
			return autowireConstructor(beanName, mbd, ctors, null);
		}

		// No special handling: simply use no-arg constructor.
		/*
		* 使用默认构造器
		* */
		/*
		* InstantiationStrategy 接口中有三个方法 ： 三个方法名称都是一样的，只是进行了重载：instantiate
		*
		* 1.	  Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner)
		*		throws BeansException;
		* 		默认调用无参的构造方法进行处理
		*
		* 2.	  Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
		*		Constructor<?> ctor, Object... args) throws BeansException;
		* 		通过指定构造器的方式进行实例化
		*
		* 3.	  Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
		*		@Nullable Object factoryBean, Method factoryMethod, Object... args)
		*		throws BeansException;
		* 		通过指定的工厂方法进行实例化
		*
		* InstantiationStrategy 接口 有个实现子类：SimpleInstantiationStrategy，对上述三个方法进行实现
		* 但是 SimpleInstantiationStrategy 中扩展了两个方法 ：
		*
		* 扩展1. 	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		*				throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
		*			}
		*
		* 扩展2.		protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName,
		*					BeanFactory owner, @Nullable Constructor<?> ctor, Object... args) {
		*
		*				throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
		*			}
		*
		* 两个扩展直接抛出异常，可由子类去实现具体逻辑
		*
		* CglibSubclassingInstantiationStrategy 就是 SimpleInstantiationStrategy 的子类
		*
		* 所以在此实例化的时候共有五种方式：
		* 		1.  SimpleInstantiationStrategy 中  默认构造器构造
		* 		2.  SimpleInstantiationStrategy 中  指定构造器
		* 		3.  SimpleInstantiationStrategy 中  指定工厂方法
		* 		4.  CglibSubclassingInstantiationStrategy 在代理子类中 使用默认构造
		* 		5.	CglibSubclassingInstantiationStrategy 在代理子类中 使用指定构造
		* */
		return instantiateBean(beanName, mbd);
	}

	/**
	 * Obtain a bean instance from the given supplier.
	 * @param instanceSupplier the configured supplier
	 * @param beanName the corresponding bean name
	 * @return a BeanWrapper for the new instance
	 * @since 5.0
	 * @see #getObjectForBeanInstance
	 */
	protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		Object instance;

		String outerBean = this.currentlyCreatedBean.get();
		this.currentlyCreatedBean.set(beanName);
		try {
			/*
			* get() 是函数式接口，相当于直接调用在 com\szu\spring\supplier\SupplierBeanFactoryPostProcessor 中修改后的
			* beanDefinition中的 CreateSupplier::createUser 直接返回 User 对象
			* */
			instance = instanceSupplier.get();
		}
		finally {
			if (outerBean != null) {
				this.currentlyCreatedBean.set(outerBean);
			}
			else {
				this.currentlyCreatedBean.remove();
			}
		}

		if (instance == null) {
			instance = new NullBean();
		}
		/*
		* 把 instance 包装秤 BeanWrapper 返回
		* */
		BeanWrapper bw = new BeanWrapperImpl(instance);
		initBeanWrapper(bw);
		return bw;
	}

	/**
	 * Overridden in order to implicitly register the currently created bean as
	 * dependent on further beans getting programmatically retrieved during a
	 * {@link Supplier} callback.
	 * @since 5.0
	 * @see #obtainFromSupplier
	 */
	@Override
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		String currentlyCreatedBean = this.currentlyCreatedBean.get();
		if (currentlyCreatedBean != null) {
			registerDependentBean(beanName, currentlyCreatedBean);
		}

		return super.getObjectForBeanInstance(beanInstance, name, beanName, mbd);
	}

	/**
	 * Determine candidate constructors to use for the given bean, checking all registered
	 * {@link SmartInstantiationAwareBeanPostProcessor SmartInstantiationAwareBeanPostProcessors}.
	 * @param beanClass the raw class of the bean
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors
	 */
	@Nullable
	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName)
			throws BeansException {

		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					Constructor<?>[] ctors = ibp.determineCandidateConstructors(beanClass, beanName);
					if (ctors != null) {
						return ctors;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Instantiate the given bean using its default constructor.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			if (System.getSecurityManager() != null) {
				beanInstance = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(mbd, beanName, this),
						getAccessControlContext());
			}
			else {
				/*
				* 获取实例化策略，默认使用CGLib代理，然后使用代理类初始化对象
				* 有methodOverride属性就创建代理子类，没有的话就直接反射创建！！！！！！！！！！！
				* */
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
			}
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);
			initBeanWrapper(bw);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * mbd parameter specifies a class, rather than a factoryBean, or an instance variable
	 * on a factory object itself configured using Dependency Injection.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 * @see #getBean(String, Object[])
	 */
	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
	}

	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param ctors the chosen candidate constructors
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {
		/*
		* AspectJPointcutAdvisor 的构造方法需要传入  AbstractAspectJAdvice
		*
		* 									  { AspectJAfterAdvice            }
		* 									  { AspectJAfterReturningAdvice   }
		* AbstractAspectJAdvice 的实现子类有 -> { AspectJAfterThrowingAdvice    } --> 每个子类的构造方法需要传入 Method + AspectJExpressionPointcut + AspectInstanceFactory
		* 									  { AspectJAroundAdvice           }
		*									  { AspectJMethodBeforeAdvice     }
		*
		* 所以在创建 AspectJPointcutAdvisor对象的时候 需要先创建   Advice + （ Method  AspectJExpressionPointcut  AspectInstanceFactory ）
		* */
 		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}

	/**
	 * Populate the bean instance in the given BeanWrapper with the property values
	 * from the bean definition.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param bw the BeanWrapper with bean instance
	 */
	@SuppressWarnings("deprecation")  // for postProcessPropertyValues
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		if (bw == null) {
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				return;
			}
		}

		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
		/*
		* hasInstantiationAwareBeanPostProcessors()   判断是否实现了 InstantiationAwareBeanPostProcessor 接口
		* 此接口用在给机会创建代理对象的时候，共有五个方法
		* */
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					/*
					* 如果自己实现了 InstantiationAwareBeanPostProcessor 接口中，自己重写了 postProcessAfterInstantiation方法
					* 在方法中给这个 类型的 bean 赋值，而且不希望自己被赋的值被下边的操作覆盖掉，所以就要在
					* postProcessAfterInstantiation方法 中返回 false，  ！false = true 直接跳出当前 populateBean方法执行
					* */
					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
						return;
					}
				}
			}
		}
		/*
		  	<bean id="person" class="com.szu.spring.populateBean.Person" autowire="byName">
				<property name="id" value="1"></property>
				<property name="gender" value="female"></property>
				<property name="age" value="25"></property>
			</bean>

		* 获取到 property 中的值存入 PVS
		*  */
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);
		/* byName == 1，等等 */
		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			// Add property values based on autowire by name if applicable.
			/*
			* 处理 byName
			* */
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				/* 通过bw的PropertyDescriptor属性名，查找出对应的Bean对象，并添加到newPvs中 */
				autowireByName(beanName, mbd, bw, newPvs);
			}
			// Add property values based on autowire by type if applicable.
			/*
			 * 处理 byType
			 *
			 * map类型的值按照类型注入的时候会产生多余的值！！！
			 * */
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}
			pvs = newPvs;
		}
		/*
		* ==============================================================================================================
		* ==============================================================================================================
		* 工厂是否有 InstantiationAwareBeanPostProcessor， 开始处理自定义的逻辑、或者开始处理 @PostConstruct @PreDestroy
		* 重中之重是 处理 @Autowired 注解！！！！
		*
		* CommonAnnotationBeanPostProcessor   AutowiredAnnotationBeanPostProcessor
		* ==============================================================================================================
		* ==============================================================================================================
		* */
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

		PropertyDescriptor[] filteredPds = null;
		if (hasInstAwareBpps) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			/*遍历工厂中的所有的后置处理器*/
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					/*
					* 如果 bp 是 InstantiationAwareBeanPostProcessor
					* 调用实现的 postProcessProperties 方法处理
					* 我们可以自己实现自己的 InstantiationAwareBeanPostProcessor 接口，然后重写postProcessProperties该方法
					*
					* ==================================================================================================
					* ==================================================================================================
					* @Autowired 注解处理时候 也是在此，
					* AutowiredAnnotationBeanPostProcessor 实现了 InstantiationAwareBeanPostProcessor 接口
					* ==================================================================================================
					* ==================================================================================================
					* */
					PropertyValues pvsToUse = ibp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
					if (pvsToUse == null) {
						if (filteredPds == null) {
							filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
						}
						pvsToUse = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
						if (pvsToUse == null) {
							return;
						}
					}
					pvs = pvsToUse;
				}
			}
		}
		if (needsDepCheck) {
			if (filteredPds == null) {
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

		if (pvs != null) {
			/*
			*  <bean class="org.springframework.beans.factory.config.CustomEditorConfigurer">
					<property name="propertyEditorRegistrars">
						<list>
							<bean class="com.szu.spring.myRegisterEditor.MyEditorRegistrar"></bean>
						</list>
					</property>
				</bean>

				如果可以获取到property标签，则开始处理 property属性， 如果里边有各种依赖的其他Bean，在此步骤中创建    String解析成Address也在这里
				    1.在此进行值转换 比如 star中有个address属性是Address类型，自己注册了自定义编辑器传入一个字符串解析成Address类型
					2.把解析到的值存入到每个 PropertyValue 的 convertedValue 属性中
			 	    3.从刚才的PropertyValue存入的convertedValue中 获取相应的属性值，并对象相应的属性赋值
			* */
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}

	/**
	 * Fill in any missing property values with references to
	 * other beans in this factory if autowire is set to "byName".
	 * @param beanName the name of the bean we're wiring up.
	 * Useful for debugging messages; not used functionally.
	 * @param mbd bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {
		/*
		* unsatisfiedNonSimpleProperties 过滤非简单属性

		* PropertyDescriptor 获取到的 bw对应的那个bean所有属性的 set、get方法，
		* 每个属性的类型名称等等信息，WriteMethod 就是 set方法， writeMethod 就是 set方法
		*
		* 当 pvs（bean标签定义的属性中） 中
		* 1.不包含当前遍历到的 属性的时候，
		* 2.当前属性有set方法的时候
		* 3.当前属性也不是简单属性的时候（Boolean, Character, Byte, Short, Integer, Long, Float, Double, Void, Array）
		*
		* propertyNames 就是符合 1.2.3.的属性集合
		* */
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			/*
			 * containsBean(propertyName)
			 * 一级缓存中有没有这个BeanName对应的Bean，beanDefinitionMap中有没有这个Name对应的BeanDefinition
			 * 返回   ！（beanName 是否以 & 开头）|| （是不是 factoryBean）
			 * */
			if (containsBean(propertyName)) {
				/* 填充属性的时候发现bean还没有的话，创建Bean */
				Object bean = getBean(propertyName);
				/* pvs中的对象属性赋值成上一步中创建的bean  */
				pvs.add(propertyName, bean);
				/*
				* 在给对象赋值的时候，新创建了一个Bean，
				* 所以此时当前bean 与 刚刚被创建的bean 肯定有依赖关系
				* 此方法中把对象的依赖关系存储起来，存起来的意义就在于如果第二次创建同一类型对象的时候可以直接使用
				* */
				registerDependentBean(propertyName, beanName);
				if (logger.isTraceEnabled()) {
					logger.trace("Added autowiring by name from bean name '" + beanName +
							"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
							"' by name: no matching bean found");
				}
			}
		}
	}

	/**
	 * Abstract method defining "autowire by type" (bean properties by type) behavior.
	 * <p>This is like PicoContainer default, in which there must be exactly one bean
	 * of the property type in the bean factory. This makes bean factories simple to
	 * configure for small namespaces, but doesn't work as well as standard Spring
	 * behavior for bigger applications.
	 * @param beanName the name of the bean to autowire by type
	 * @param mbd the merged bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByType(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {
		/*
		* 获取是否有自定义的类型转换器
		* */
		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			/*
			* 如果没有自定义的类型转换器就把bw给转换器，因为 BeanWrapper 实现了 TypeConverter 接口
			* */
			converter = bw;
		}

		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
		/*
		 * unsatisfiedNonSimpleProperties 过滤非简单属性
		 * */
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			try {
				/*
				 * PropertyDescriptor 获取到的 bw对应的那个bean所有属性的 set、get方法，
				 * 每个属性的类型名称等等信息，WriteMethod 就是 set方法， writeMethod 就是 set方法
				 * */
				PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
				/* Don't try autowiring by type for type Object: never makes sense,*/
				// even if it technically is a unsatisfied, non-simple property.
				if (Object.class != pd.getPropertyType()) {
					MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);  /* 获取set方法的参数 */
					// Do not allow eager init for type matching in case of a prioritized post-processor.
					boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
					/* 把刚才的 PropertyDescriptor 包装成了 DependencyDescriptor
					* 二者的区别就是 */
					DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
					/*
					* 根据desc的依赖类型解析出 descriptor 所包装的对象匹配的候选Bean对象
					* */
					Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
					if (autowiredArgument != null) {
						pvs.add(propertyName, autowiredArgument);
					}
					for (String autowiredBeanName : autowiredBeanNames) {
						registerDependentBean(autowiredBeanName, beanName);
						if (logger.isTraceEnabled()) {
							logger.trace("Autowiring by type from bean name '" + beanName + "' via property '" +
									propertyName + "' to bean named '" + autowiredBeanName + "'");
						}
					}
					autowiredBeanNames.clear();
				}
			}
			catch (BeansException ex) {
				throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
			}
		}
	}


	/**
	 * Return an array of non-simple bean properties that are unsatisfied.
	 * These are probably unsatisfied references to other beans in the
	 * factory. Does not include simple properties like primitives or Strings.
	 * @param mbd the merged bean definition the bean was created with
	 * @param bw the BeanWrapper the bean was created with
	 * @return an array of bean property names
	 * @see org.springframework.beans.BeanUtils#isSimpleProperty
	 */
	protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
		Set<String> result = new TreeSet<>();
		/* 获取所有的 mbd 属性值 */
		PropertyValues pvs = mbd.getPropertyValues();
		/*
		* PropertyDescriptor 获取到的 bw对应的那个bean所有属性的 set、get方法，
		* 每个属性的类型名称等等信息，WriteMethod 就是 set方法， writeMethod 就是 set方法
		* */
		PropertyDescriptor[] pds = bw.getPropertyDescriptors();
		for (PropertyDescriptor pd : pds) {
			/*
			* 当 pvs（bean标签定义的属性中） 中
			* 1.不包含当前遍历到的 属性的时候，
			* 2.当前属性有set方法的时候
			* 3.当前属性也不是简单属性的时候（Boolean, Character, Byte, Short, Integer, Long, Float, Double, Void, Array）
			* 把当前属性添加到result中待返回
			* */
			if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
					!BeanUtils.isSimpleProperty(pd.getPropertyType())) {
				result.add(pd.getName());
			}
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @param cache whether to cache filtered PropertyDescriptors for the given bean Class
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 * @see #filterPropertyDescriptorsForDependencyCheck(org.springframework.beans.BeanWrapper)
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
		PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
		if (filtered == null) {
			filtered = filterPropertyDescriptorsForDependencyCheck(bw);
			if (cache) {
				PropertyDescriptor[] existing =
						this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
				if (existing != null) {
					filtered = existing;
				}
			}
		}
		return filtered;
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
		List<PropertyDescriptor> pds = new ArrayList<>(Arrays.asList(bw.getPropertyDescriptors()));
		pds.removeIf(this::isExcludedFromDependencyCheck);
		return pds.toArray(new PropertyDescriptor[0]);
	}

	/**
	 * Determine whether the given bean property is excluded from dependency checks.
	 * <p>This implementation excludes properties defined by CGLIB and
	 * properties whose type matches an ignored dependency type or which
	 * are defined by an ignored dependency interface.
	 * @param pd the PropertyDescriptor of the bean property
	 * @return whether the bean property is excluded
	 * @see #ignoreDependencyType(Class)
	 * @see #ignoreDependencyInterface(Class)
	 */
	protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
		return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
				this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
				AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
	}

	/**
	 * Perform a dependency check that all properties exposed have been set,
	 * if desired. Dependency checks can be objects (collaborating beans),
	 * simple (primitives and String), or all (both).
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition the bean was created with
	 * @param pds the relevant property descriptors for the target bean
	 * @param pvs the property values to be applied to the bean
	 * @see #isExcludedFromDependencyCheck(java.beans.PropertyDescriptor)
	 */
	protected void checkDependencies(
			String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, @Nullable PropertyValues pvs)
			throws UnsatisfiedDependencyException {

		int dependencyCheck = mbd.getDependencyCheck();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && (pvs == null || !pvs.contains(pd.getName()))) {
				boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
				boolean unsatisfied = (dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_ALL) ||
						(isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
						(!isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
				if (unsatisfied) {
					throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, pd.getName(),
							"Set this property value or disable dependency checking for this bean.");
				}
			}
		}
	}

	/**
	 * Apply the given property values, resolving any runtime references
	 * to other beans in this bean factory. Must use deep copy, so we
	 * don't permanently modify this property.
	 * @param beanName the bean name passed for better exception information
	 * @param mbd the merged bean definition
	 * @param bw the BeanWrapper wrapping the target object
	 * @param pvs the new property values
	 */
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		if (pvs.isEmpty()) {
			return;
		}

		if (System.getSecurityManager() != null && bw instanceof BeanWrapperImpl) {
			((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
		}

		MutablePropertyValues mpvs = null;
		/*
		* 	<bean id="studentConverter" class="com.szu.spring.myConverter.StudentConverter"></bean>

			<bean id="conversionService" class="org.springframework.context.support.ConversionServiceFactoryBean">
				<property name="converters">
					<set>
						<ref bean="studentConverter"></ref>
					</set>
				</property>
			</bean>
		*
		* 用来存储 conversionService 中的 property属性
		* */
		List<PropertyValue> original;

		if (pvs instanceof MutablePropertyValues) {
			mpvs = (MutablePropertyValues) pvs;
			if (mpvs.isConverted()) {
				// Shortcut: use the pre-converted values as-is.
				try {
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values", ex);
				}
			}
			/* 获取到property中的属性： */
			original = mpvs.getPropertyValueList();
		}
		else {
			original = Arrays.asList(pvs.getPropertyValues());
		}

		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

		// Create a deep copy, resolving any references for values.
		/*
		* 创建一个深拷贝数组， 数组中保存所有的解析到的当前Bean创建时依赖的 Bean对象，
		* 但是暂时没有给当前创建的 Bean 进行赋值
		* */
		List<PropertyValue> deepCopy = new ArrayList<>(original.size());
		boolean resolveNecessary = false;
		for (PropertyValue pv : original) {
			if (pv.isConverted()) {
				deepCopy.add(pv);
			}
			else {
				/*
				* 获取到 property 的属性名 和里边的内容 set集合 List Map等等
				* */
				String propertyName = pv.getName();
				Object originalValue = pv.getValue();
				if (originalValue == AutowiredPropertyMarker.INSTANCE) {
					Method writeMethod = bw.getPropertyDescriptor(propertyName).getWriteMethod();
					if (writeMethod == null) {
						throw new IllegalArgumentException("Autowire marker for property without write method: " + pv);
					}
					originalValue = new DependencyDescriptor(new MethodParameter(writeMethod, 0), true);
				}
				/*
				* 解析各种类型，
				* RuntimeBeanReference				---->
				* RuntimeBeanNameReference		    ---->
				* BeanDefinitionHolder				---->
				* BeanDefinition					---->
				* DependencyDescriptor				---->
				* ManagedArray						---->
				* ManagedList						---->
				* ManagedSet						---->
				* ManagedMap						---->
				* ManagedProperties					---->
				* TypedStringValue					---->
				* NullBean							---->
				* */
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
				Object convertedValue = resolvedValue;
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				if (convertible) { /* 在此进行值转换 比如 star中有个address属性是Address类型，自己注册了自定义编辑器传入一个字符串解析成Address类型 */
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				// Possibly store converted value in merged bean definition,
				// in order to avoid re-conversion for every created bean instance.
				if (resolvedValue == originalValue) {
					if (convertible) {
						pv.setConvertedValue(convertedValue); /* 把解析到的值存入到每个 PropertyValue 的 convertedValue 属性中 */
					}
					deepCopy.add(pv); /* 转换之后的值加入到深拷贝的数组 */
				}
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				}
				else {
					resolveNecessary = true;
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
		if (mpvs != null && !resolveNecessary) {
			mpvs.setConverted();
		}

		// Set our (possibly massaged) deep copy.
		try {
			bw.setPropertyValues(new MutablePropertyValues(deepCopy)); /* 从刚才的 deepCopy 存入的convertedValue中 获取相应的属性值，并对象相应的属性赋值 */
		}
		catch (BeansException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Error setting property values", ex);
		}
	}

	/**
	 * Convert the given value for the specified target property.
	 */
	@Nullable
	private Object convertForProperty(
			@Nullable Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

		if (converter instanceof BeanWrapperImpl) {
			return ((BeanWrapperImpl) converter).convertForProperty(value, propertyName); /* 字符串解析成Address等等 */
		}
		else {
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
			return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
		}
	}


	/**
	 * Initialize the given bean instance, applying factory callbacks
	 * as well as init methods and bean post processors.
	 * <p>Called from {@link #createBean} for traditionally defined beans,
	 * and from {@link #initializeBean} for existing bean instances.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @return the initialized bean instance (potentially wrapped)
	 * @see BeanNameAware
	 * @see BeanClassLoaderAware
	 * @see BeanFactoryAware
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #invokeInitMethods
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		}
		else {
			/*
			* 如果bean实现了Aware接口的话，在此处
			* 里边处理了三个  BeanFactoryAware BeanClassLoaderAware BeanNameAware
			* 正好对应前边忽略 ignore了三个
			*
			* obtainFreshBeanFactory() ->  refreshBeanFactory() -> createBeanFactory() -> new DefaultListableBeanFactory(getInternalParentBeanFactory())
			*
			* 		                                         {    ignoreDependencyInterface(BeanNameAware.class);
			* 		-> AbstractAutowireCapableBeanFactory -> {    ignoreDependencyInterface(BeanFactoryAware.class);
			* 												 {    ignoreDependencyInterface(BeanClassLoaderAware.class);
			*
			* 为什么单独忽略他们三个呢？这里又为什么单独处理他们三个呢？
			 * */
			invokeAwareMethods(beanName, bean);
		}

		Object wrappedBean = bean;
		if (mbd == null || !mbd.isSynthetic()) {
			/*
			* postProcessBeforeInitialization
			* */
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

		try {
			/*
			* init-method也在此处执行
			* 如果 bean 对象是 InitializingBean 类型的，那么进入判断，执行这个对象的 afterPropertiesSet（）方法
			* 在 afterPropertiesSet（） 可以自己随便改这个类型的bean的属性值
			* */
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}
		if (mbd == null || !mbd.isSynthetic()) {
			/*
			* postProcessAfterInitialization
			* */
			/*
			 * 配置了事务的话会在这里创建事务对象！！！！！！！！！！！！！！！！！！ AbstractAutowireCapableBeanFactory
			 * */
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}

		return wrappedBean;
	}

	private void invokeAwareMethods(String beanName, Object bean) {
		if (bean instanceof Aware) {
			if (bean instanceof BeanNameAware) {
				((BeanNameAware) bean).setBeanName(beanName);
			}
			if (bean instanceof BeanClassLoaderAware) {
				ClassLoader bcl = getBeanClassLoader();
				if (bcl != null) {
					((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
				}
			}
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}

	/**
	 * Give a bean a chance to react now all its properties are set,
	 * and a chance to know about its owning bean factory (this object).
	 * This means checking whether the bean implements InitializingBean or defines
	 * a custom init method, and invoking the necessary callback(s) if it does.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the merged bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @throws Throwable if thrown by init methods or by the invocation process
	 * @see #invokeCustomInitMethod
	 */
	protected void invokeInitMethods(String beanName, Object bean, @Nullable RootBeanDefinition mbd)
			throws Throwable {
		/*
		* 如果当前bean对象是 InitializingBean 类型的，那么进入判断，执行这个对象的 afterPropertiesSet（）方法
		*
		* InitializingBean是干啥的呢？
		* 实现这些接口的Bean 需要在BeanFactory设置了它们的所有属性后做出反应
		* 比如 Person 实现了这个接口，在这个方法里进行 personName 属性值的长度检验，大于25字符会怎么 小于25字符又会怎么
		*
		* 就是在 afterPropertiesSet（） 可以自己随便改这个类型的bean的属性值了
		* */
		boolean isInitializingBean = (bean instanceof InitializingBean);
		if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			}
			if (System.getSecurityManager() != null) {
				try {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						((InitializingBean) bean).afterPropertiesSet();
						return null;
					}, getAccessControlContext());
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				((InitializingBean) bean).afterPropertiesSet();
			}
		}

		/*
		* 如果满足这些条件，继续调用 init 方法。
		* 目测是 @PostConstruct 标记的方法吧
		* */
		if (mbd != null && bean.getClass() != NullBean.class) {
			String initMethodName = mbd.getInitMethodName();
			if (StringUtils.hasLength(initMethodName) &&
					!(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.isExternallyManagedInitMethod(initMethodName)) {
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}

	/**
	 * Invoke the specified custom init method on the given bean.
	 * Called by invokeInitMethods.
	 * <p>Can be overridden in subclasses for custom resolution of init
	 * methods with arguments.
	 * @see #invokeInitMethods
	 */
	protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd)
			throws Throwable {

		String initMethodName = mbd.getInitMethodName();
		Assert.state(initMethodName != null, "No init method set");
		Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));

		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				throw new BeanDefinitionValidationException("Could not find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("No default init method named '" + initMethodName +
							"' found on bean with name '" + beanName + "'");
				}
				// Ignore non-existent default lifecycle methods.
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
		}
		Method methodToInvoke = ClassUtils.getInterfaceMethodIfPossible(initMethod);

		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(methodToInvoke);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>)
						() -> methodToInvoke.invoke(bean), getAccessControlContext());
			}
			catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		}
		else {
			try {
				ReflectionUtils.makeAccessible(methodToInvoke);
				methodToInvoke.invoke(bean);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * Applies the {@code postProcessAfterInitialization} callback of all
	 * registered BeanPostProcessors, giving them a chance to post-process the
	 * object obtained from FactoryBeans (for example, to auto-proxy them).
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	@Override
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
		return applyBeanPostProcessorsAfterInitialization(object, beanName);
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanInstanceCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanInstanceCache.clear();
		}
	}

	/**
	 * Expose the logger to collaborating delegates.
	 * @since 5.0.7
	 */
	Log getLogger() {
		return logger;
	}


	/**
	 * Special DependencyDescriptor variant for Spring's good old autowire="byType" mode.
	 * Always optional; never considering the parameter name for choosing a primary candidate.
	 */
	@SuppressWarnings("serial")
	private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {

		public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
			super(methodParameter, false, eager);
		}

		@Override
		public String getDependencyName() {
			return null;
		}
	}


	/**
	 * {@link MethodCallback} used to find {@link FactoryBean} type information.
	 */
	private static class FactoryBeanMethodTypeFinder implements MethodCallback {

		private final String factoryMethodName;

		private ResolvableType result = ResolvableType.NONE;

		FactoryBeanMethodTypeFinder(String factoryMethodName) {
			this.factoryMethodName = factoryMethodName;
		}

		@Override
		public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
			if (isFactoryBeanMethod(method)) {
				ResolvableType returnType = ResolvableType.forMethodReturnType(method);
				ResolvableType candidate = returnType.as(FactoryBean.class).getGeneric();
				if (this.result == ResolvableType.NONE) {
					this.result = candidate;
				}
				else {
					Class<?> resolvedResult = this.result.resolve();
					Class<?> commonAncestor = ClassUtils.determineCommonAncestor(candidate.resolve(), resolvedResult);
					if (!ObjectUtils.nullSafeEquals(resolvedResult, commonAncestor)) {
						this.result = ResolvableType.forClass(commonAncestor);
					}
				}
			}
		}

		private boolean isFactoryBeanMethod(Method method) {
			return (method.getName().equals(this.factoryMethodName) &&
					FactoryBean.class.isAssignableFrom(method.getReturnType()));
		}

		ResolvableType getResult() {
			Class<?> resolved = this.result.resolve();
			boolean foundResult = resolved != null && resolved != Object.class;
			return (foundResult ? this.result : ResolvableType.NONE);
		}
	}

}
