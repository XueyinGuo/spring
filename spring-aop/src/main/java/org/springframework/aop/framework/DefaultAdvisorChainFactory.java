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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, @Nullable Class<?> targetClass) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		/*
		* 获取到一个 DefaultAdvisorAdapterRegistry 对象，这个对象中包含了三个适配器
		* MethodBeforeAdviceAdapter
		* AfterReturningAdviceAdapter
		* ThrowsAdviceAdapter
		* */
		/*
		 * PS:当前有6个 Advisor， 分别是 ExposeInvocationInterceptor，Around,After 等等，
		 * 只有 ExposeInvocationInterceptor、AspectAfterAdvice、AspectJAfterThrowingAdvice、AspectAroundAdvice 实现了 MethodInterceptor 接口
		 * 所以 ExposeInvocationInterceptor、AspectJAfterAdvice、AspectJAfterThrowingAdvice、AspectAroundAdvice 在第一个判断的时候就可以被添加到 interceptors 中待返回
		 *
		 * 然而  AspectJMethodBeforeAdvice 、 AspectJAfterReturningAdvice怎么办呢？
		 *
		 * 答案就在  DefaultAdvisorAdapterRegistry 对象中 ，这个对象中包含了三个适配器
		 * 						1.MethodBeforeAdviceAdapter
		 * 						2.AfterReturningAdviceAdapter
		 * 						3.ThrowsAdviceAdapter
		 *
		 *
		 * 通过前两个适配器 MethodBeforeAdviceAdapter  AfterReturningAdviceAdapter可以分别处理 AspectJMethodBeforeAdvice 、 AspectJAfterReturningAdvice，
		 *
		 * 适配器中有两个方法：supportsAdvice   和   getInterceptor，
		 * 						|                     |
		 * 						|                     |
		 * 				 （判断是否是当）			 （返回一个新对象加入到待返回集合）
		 * 				（前适配器支持的类） 	     （这个新对象实现了 MethodInterceptor 接口，两个适配器分别返回了）
		 * 										 （ AfterReturningAdviceInterceptor MethodBeforeAdviceInterceptor）
		 *
		 * 所以 第三个适配器是干什么的？
		 *	TODO ThrowsAdvice 是干啥的？？？？
		 * */
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
		/* 获取到当前的 六个 Advisor */
		Advisor[] advisors = config.getAdvisors();
		List<Object> interceptorList = new ArrayList<>(advisors.length);
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		Boolean hasIntroductions = null;

		for (Advisor advisor : advisors) {
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally.
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
				/* 如果当前的 Advisor 适用于目标类， */
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					boolean match;
					/* 检测 Advisor 是否适用于 当前方法 */
					if (mm instanceof IntroductionAwareMethodMatcher) {
						if (hasIntroductions == null) {
							hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
						}
						match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
					}
					else {
						match = mm.matches(method, actualClass);
					}
					if (match) {
						/*
						* 拦截器链是通过 AdvisorAdapterRegistry 来加入的，这个AdvisorAdapterRegistry堆advice织入有很大作用
						* 此时的 registry 是上边刚刚创建的 DefaultAdvisorAdapterRegistry，里边有三个适配器
						* */
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}
		/*
		* 经过这个 for 循环之后，把所有的拦截器链加入到了 列表中。
		* 此时拦截器链中的是 6个Advisor中所包含Advice 的其中4个，
		* 和另外两个 适配器返回的 对应Advice的 实现了MethodInterceptor接口的实现类
		* */
		return interceptorList;
	}

	/**
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}

}
