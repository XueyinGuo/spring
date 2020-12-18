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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.lang.Nullable;

/**
 * Base class for {@link org.springframework.context.ApplicationContext}
 * implementations which are supposed to support multiple calls to {@link #refresh()},
 * creating a new internal bean factory instance every time.
 * Typically (but not necessarily), such a context will be driven by
 * a set of config locations to load bean definitions from.
 *
 * <p>The only method to be implemented by subclasses is {@link #loadBeanDefinitions},
 * which gets invoked on each refresh. A concrete implementation is supposed to load
 * bean definitions into the given
 * {@link org.springframework.beans.factory.support.DefaultListableBeanFactory},
 * typically delegating to one or more specific bean definition readers.
 *
 * <p><b>Note that there is a similar base class for WebApplicationContexts.</b>
 * {@link org.springframework.web.context.support.AbstractRefreshableWebApplicationContext}
 * provides the same subclassing strategy, but additionally pre-implements
 * all context functionality for web environments. There is also a
 * pre-defined way to receive config locations for a web context.
 *
 * <p>Concrete standalone subclasses of this base class, reading in a
 * specific bean definition format, are {@link ClassPathXmlApplicationContext}
 * and {@link FileSystemXmlApplicationContext}, which both derive from the
 * common {@link AbstractXmlApplicationContext} base class;
 * {@link org.springframework.context.annotation.AnnotationConfigApplicationContext}
 * supports {@code @Configuration}-annotated classes as a source of bean definitions.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 1.1.3
 * @see #loadBeanDefinitions
 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory
 * @see org.springframework.web.context.support.AbstractRefreshableWebApplicationContext
 * @see AbstractXmlApplicationContext
 * @see ClassPathXmlApplicationContext
 * @see FileSystemXmlApplicationContext
 * @see org.springframework.context.annotation.AnnotationConfigApplicationContext
 */
public abstract class AbstractRefreshableApplicationContext extends AbstractApplicationContext {

	@Nullable
	private Boolean allowBeanDefinitionOverriding;

	@Nullable
	private Boolean allowCircularReferences;

	/** Bean factory for this context. */
	@Nullable
	private volatile DefaultListableBeanFactory beanFactory;


	/**
	 * Create a new AbstractRefreshableApplicationContext with no parent.
	 */
	public AbstractRefreshableApplicationContext() {
	}

	/**
	 * Create a new AbstractRefreshableApplicationContext with the given parent context.
	 * @param parent the parent context
	 */
	public AbstractRefreshableApplicationContext(@Nullable ApplicationContext parent) {
		super(parent);
	}


	/**
	 * Set whether it should be allowed to override bean definitions by registering
	 * a different definition with the same name, automatically replacing the former.
	 * If not, an exception will be thrown. Default is "true".
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowBeanDefinitionOverriding
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * Set whether to allow circular references between beans - and automatically
	 * try to resolve them.
	 * <p>Default is "true". Turn this off to throw an exception when encountering
	 * a circular reference, disallowing them completely.
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowCircularReferences
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}


	/**
	 * This implementation performs an actual refresh of this context's underlying
	 * bean factory, shutting down the previous bean factory (if any) and
	 * initializing a fresh bean factory for the next phase of the context's lifecycle.
	 */
	@Override
	protected final void refreshBeanFactory() throws BeansException {
		/*
		* 如果Bean工厂已经存在，则进行销毁之后再创建一个新的BeanFactory
		* */
		if (hasBeanFactory()) {
			destroyBeans();
			closeBeanFactory();
		}
		try {
			/*
			* 创建一个新的DefaultListableBeanFactory对象
			* 设置了一些Aware接口的忽略
			* 还设置了一些比较重要的属性值
			* 比如：allowBeanDefinitionOverriding 和 allowCircularReference，
			* 除了一些关键位置外，其余的还都是默认值
			* */
			DefaultListableBeanFactory beanFactory = createBeanFactory();
			/*
			* 以下都是为beanFactory的赋值，构造成一个更完整的工厂
			* 创建完新的BeanFactory之后，为其指定了一个序列化ID
			* 以便在之后的反序列时候通过这个ID直接得到Bean工厂对象
			* ID在AbstractApplicationContext中
			* private String id = ObjectUtils.identityToString(this);时设置完成
			* */
			beanFactory.setSerializationId(getId());
			/*
			* customizeBeanFactory定制化自己的工厂：
			* 虽然刚刚初始化完的而且设置完序列化ID的bean工厂已经被赋值为
            * allowBeanDefinitionOverriding 和 allowCircularReferences都是true
            * 这些true都是默认的，如果我们在程序运行过程中不允许覆盖同名bean也不允许循环依赖
            * 我们可以直接操作这个类中的这两个变量，把工厂的默认允许给覆盖掉，所以这又是Spring给我们留着的
            * 一个扩展点，重写这个方法就完了！！！！！！
			* */
			customizeBeanFactory(beanFactory);
			/*
			* ！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
			* ！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
			* ！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
			* 重中之重！加载xml配置文件的，我们的bean定义信息需要从xml提取出来存进工厂类，
			* 所以此处吧beanFactory当成参数传递进去，往beanFactory里进行一些属性设置工作，
			*
			* 1.为给定的BeanFactory创建一个新的XmlBeanDefinitionReader，
			* 这是一个XML文件读取器，专门解析XML文件的
			* 2.EntityResolver
			* XML整体的结构是什么样的，然鹅这个整体的结构是在xsd中存放，所以需要先加载xsd，
			* 3.设置是否对XML进行验证，默认为true
			* 用dtd文件格式还是用xsd文件格式对自己的xml进行验证
			* 4.loadBeanDefinitions()  方法重载好多次！！！！！！ ，String[]就是所有需要解析的XML文档的文件名
			* 总的流程就是从 String[] -> String -> Resource[] -> Resource -> Document -> BeanDefinition对象，
			* */
			loadBeanDefinitions(beanFactory);
			this.beanFactory = beanFactory;
		}
		catch (IOException ex) {
			throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
		}
	}

	@Override
	protected void cancelRefresh(BeansException ex) {
		DefaultListableBeanFactory beanFactory = this.beanFactory;
		if (beanFactory != null) {
			beanFactory.setSerializationId(null);
		}
		super.cancelRefresh(ex);
	}

	@Override
	protected final void closeBeanFactory() {
		DefaultListableBeanFactory beanFactory = this.beanFactory;
		if (beanFactory != null) {
			beanFactory.setSerializationId(null);
			this.beanFactory = null;
		}
	}

	/**
	 * Determine whether this context currently holds a bean factory,
	 * i.e. has been refreshed at least once and not been closed yet.
	 */
	protected final boolean hasBeanFactory() {
		return (this.beanFactory != null);
	}

	@Override
	public final ConfigurableListableBeanFactory getBeanFactory() {
		DefaultListableBeanFactory beanFactory = this.beanFactory;
		if (beanFactory == null) {
			throw new IllegalStateException("BeanFactory not initialized or already closed - " +
					"call 'refresh' before accessing beans via the ApplicationContext");
		}
		return beanFactory;
	}

	/**
	 * Overridden to turn it into a no-op: With AbstractRefreshableApplicationContext,
	 * {@link #getBeanFactory()} serves a strong assertion for an active context anyway.
	 */
	@Override
	protected void assertBeanFactoryActive() {
	}

	/**
	 * Create an internal bean factory for this context.
	 * Called for each {@link #refresh()} attempt.
	 * <p>The default implementation creates a
	 * {@link org.springframework.beans.factory.support.DefaultListableBeanFactory}
	 * with the {@linkplain #getInternalParentBeanFactory() internal bean factory} of this
	 * context's parent as parent bean factory. Can be overridden in subclasses,
	 * for example to customize DefaultListableBeanFactory's settings.
	 * @return the bean factory for this context
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowBeanDefinitionOverriding
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowEagerClassLoading
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowCircularReferences
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowRawInjectionDespiteWrapping
	 */
	protected DefaultListableBeanFactory createBeanFactory() {
		/*
		* getInternalParentBeanFactory() 这还有一步获取父类Bean工厂的操作
		* */
		/*
		 * getInternalParentBeanFactory()只这样做判断的
		 * 如果是ConfigurableApplicationContext类型，则直接返回父类的Bean工厂
		 * 如果不是直接返回父类
		 * */
		/*
		 * new DefaultListableBeanFactory() 中调用父类的
		 * super中设置 beanClassLoader 类加载器，用来处理bean的名称
		 * CacheBeanMetadata 设置bean元数据的缓存（缓存干嘛先不管）
		 * 剩下还有一堆的属性值设置，其中比较重要的是：
		 * allowCircularReference = true（是否解决循环依赖的问题）
		 * allowBeanDefinitionOverriding = true
		 * */
		/*
		 * 还设置了一些忽略依赖的Aware接口
		 * 【在bean创建过程中，我们会进行一系列的Aware接口的处理，叫做invokeAwareMethods用来统一处理】
		 * 【Aware接口的一些基本操作】
		 * 这里设置忽略是为了在bean创建过程中取统一处理，而不是在这里就进行处理，所以先设置了忽略
		 * TODO 写和不写ignoreDependencyInterface的小区别，请见下回分解
		 * */
		return new DefaultListableBeanFactory(getInternalParentBeanFactory());
	}

	/**
	 * Customize the internal bean factory used by this context.
	 * Called for each {@link #refresh()} attempt.
	 * <p>The default implementation applies this context's
	 * {@linkplain #setAllowBeanDefinitionOverriding "allowBeanDefinitionOverriding"}
	 * and {@linkplain #setAllowCircularReferences "allowCircularReferences"} settings,
	 * if specified. Can be overridden in subclasses to customize any of
	 * {@link DefaultListableBeanFactory}'s settings.
	 * @param beanFactory the newly created bean factory for this context
	 * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding
	 * @see DefaultListableBeanFactory#setAllowCircularReferences
	 * @see DefaultListableBeanFactory#setAllowRawInjectionDespiteWrapping
	 * @see DefaultListableBeanFactory#setAllowEagerClassLoading
	 */
	protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
		/*
		* 如果属性allowBeanDefinitionOverriding不为空，设置给beanFactory对象相应属性，是否允许覆盖同名称的不同定义的对象
		*
		* 比如两个在开发中用的很少的两个标签
		* <beans>
		 	<bean id="person" class="com.sztu.spring.Person">
		 		<lookup-method/>
		 		<replaced-method/>
		 	<bean>
		* <beans>
		*
		* */
		if (this.allowBeanDefinitionOverriding != null) {
			beanFactory.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
		}
		/*
		* 如果属性allowCircularReferences不为空，设置给beanFactory对象相应的属性，是否允许bean之间存在循环依赖
		*
		*
		* */
		if (this.allowCircularReferences != null) {
			beanFactory.setAllowCircularReferences(this.allowCircularReferences);
		}
		/*
		* 虽然刚刚初始化完的而且设置完序列化ID的bean工厂已经被赋值为
		* allowBeanDefinitionOverriding 和 allowCircularReferences都是true
		* 这些true都是默认的，如果我们在程序运行过程中不允许覆盖同名bean也不允许循环依赖
		* 我们可以直接操作这个类中的这两个变量，把工厂的默认允许给覆盖掉，所以这又是Spring给我们留着的
		* 一个扩展点，重写这个方法就完了！！！！！！
		* */
	}

	/**
	 * Load bean definitions into the given bean factory, typically through
	 * delegating to one or more bean definition readers.
	 * @param beanFactory the bean factory to load bean definitions into
	 * @throws BeansException if parsing of the bean definitions failed
	 * @throws IOException if loading of bean definition files failed
	 * @see org.springframework.beans.factory.support.PropertiesBeanDefinitionReader
	 * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader
	 */
	protected abstract void loadBeanDefinitions(DefaultListableBeanFactory beanFactory)
			throws BeansException, IOException;

}
