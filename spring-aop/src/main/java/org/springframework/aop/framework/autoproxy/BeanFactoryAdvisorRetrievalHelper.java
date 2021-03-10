/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.aop.framework.autoproxy;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving standard Spring Advisors from a BeanFactory,
 * for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AbstractAdvisorAutoProxyCreator
 */
public class BeanFactoryAdvisorRetrievalHelper {

	private static final Log logger = LogFactory.getLog(BeanFactoryAdvisorRetrievalHelper.class);

	private final ConfigurableListableBeanFactory beanFactory;

	@Nullable
	private volatile String[] cachedAdvisorBeanNames;


	/**
	 * Create a new BeanFactoryAdvisorRetrievalHelper for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAdvisorRetrievalHelper(ConfigurableListableBeanFactory beanFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		this.beanFactory = beanFactory;
	}


	/**
	 * Find all eligible Advisor beans in the current bean factory,
	 * ignoring FactoryBeans and excluding beans that are currently in creation.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	/*
	* 找到当前容器中存在的 Advisor
	* */
	public List<Advisor> findAdvisorBeans() {
		// Determine list of advisor bean names, if not cached already.
		String[] advisorNames = this.cachedAdvisorBeanNames;
		if (advisorNames == null) {
			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the auto-proxy creator apply to them!
			/*
			* 获取当前BeanFactory 和 当前容器的父BeanFactory 中所有Advisor类型的Bean的名称
			* 递归调用，一直找到最上一层位置，找到所有的 Advisor
			*
			* 这个方法和什么类似呢？ 在 BFPP的时候 你是否记得 getNameForType(BDRPP.class)筛选所有的实现了 BDRPP 接口的 Bean名称
			* */
			advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
					this.beanFactory, Advisor.class, true, false);
			/* 缓存所有的 Advisor名字 */
			this.cachedAdvisorBeanNames = advisorNames;
		}
		if (advisorNames.length == 0) {
			return new ArrayList<>();
		}

		List<Advisor> advisors = new ArrayList<>();
		for (String name : advisorNames) {
			if (isEligibleBean(name)) {
				/* 如果当前bean还创建过程中，就略过，创建完成之后再为其判断是否需要织入切面逻辑 */
				if (this.beanFactory.isCurrentlyInCreation(name)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Skipping currently created advisor '" + name + "'");
					}
				}
				else {
					try {
						/*
						* 创建bean,并添加到 List 中，待返回
						*
						* 1. 纯 AOP对象的创建
						*
						*			此处创建对象有意思的地方在于：之前创建一个半成品对象，是直接反射调用无参构造，然后调用Set方法去给属性赋值，所谓的实例化初始化时分开的
						*			但是，在这些Advisor类中没有无参构造，只有一个有参构造，所以在这里创建 Advisor对象的时候，我必须先把有参构造方法中
						*				要求的参数先创建好。所以这里的对象的创造，是需要很多层的嵌套的（跨方法递归的）。
						*
						*			       		          		   	   { -----> MethodLocatingFactoryBean
						*				Advisor --->   adviceDef --->  { -----> expression="execution(Integer com.szu.spring.aopTest.MyCalculator.*(Integer,Integer))"
						*					|		      |            { -----> SimpleBeanFactoryAwareAspectInstanceFactory
						*					|			  |								|
						*				  有参			有参							三个无参，但是第二个对象 expression 是 RunTimeReference，而且作用域是原型模式
						*																	后边这四个对象是原型模式的，只有advisor是单例模式的，根本不放进一级缓存
						*			意思就是说在创建 Advisor 的时候， 三个嵌套对象也要创建好
						*
						* 2. 事务相关的创建
						*
						*			<aop:advisor advice-ref="myAdvice" pointcut-ref="txPoint"></aop:advisor>
						* 			此时如果创建 Advisor对象，是一环套两环：但是创建的时候， txPoint 是需要直接创建的，myAdvice没直接创建，而是先加入到了一个 deepCopy 中
						* 			具体创建过程是在为某一个Bean创建代理的时候需要获取到所有的这个Bean适用的 Advisor，但是在获取的时候 myAdvice作为事务，还没有创建，所以那个时候
						* 			再进行创建
						*
						* 					2.1 创建 myAdvice，
						*
						* 							<tx:advice id="myAdvice" transaction-manager="transactionManager">
						*								<tx:attributes>
						*									<tx:method name="*"/>
						*									<tx:method name="checkout" propagation="REQUIRED"></tx:method>
						*									<tx:method name="get*" read-only="true"/>
						*								</tx:attributes>
						*							</tx:advice>
						*
						* 							此时的 myAdvice 对应的 BeanDefinition 的结构如下
						*
						*							TransactionInterceptor（相当于 Advisor）   ------>   NameMatchTransactionAttributeSource （相当于 Advice，）
						* 																								（里边所有的 Method相当于创建Advice时那三个参数）
						*
						* 					2.2 创建 txPoint
						*
						* 							<aop:pointcut id="txPoint" expression="execution(* com.szu.spring.txTest.*.*.*(..))"/>
						* 							也就是相当于 Advice 构造函数中的 第二个参数
						*
						* */
						advisors.add(this.beanFactory.getBean(name, Advisor.class));
					}
					catch (BeanCreationException ex) {
						Throwable rootCause = ex.getMostSpecificCause();
						if (rootCause instanceof BeanCurrentlyInCreationException) {
							BeanCreationException bce = (BeanCreationException) rootCause;
							String bceBeanName = bce.getBeanName();
							if (bceBeanName != null && this.beanFactory.isCurrentlyInCreation(bceBeanName)) {
								if (logger.isTraceEnabled()) {
									logger.trace("Skipping advisor '" + name +
											"' with dependency on currently created bean: " + ex.getMessage());
								}
								// Ignore: indicates a reference back to the bean we're trying to advise.
								// We want to find advisors other than the currently created bean itself.
								continue;
							}
						}
						throw ex;
					}
				}
			}
		}
		return advisors;
	}

	/**
	 * Determine whether the aspect bean with the given name is eligible.
	 * <p>The default implementation always returns {@code true}.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
