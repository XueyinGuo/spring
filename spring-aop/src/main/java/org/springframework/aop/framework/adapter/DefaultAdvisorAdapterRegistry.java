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

package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

/**
 * Default implementation of the {@link AdvisorAdapterRegistry} interface.
 * Supports {@link org.aopalliance.intercept.MethodInterceptor},
 * {@link org.springframework.aop.MethodBeforeAdvice},
 * {@link org.springframework.aop.AfterReturningAdvice},
 * {@link org.springframework.aop.ThrowsAdvice}.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {

	private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


	/**
	 * Create a new DefaultAdvisorAdapterRegistry, registering well-known adapters.
	 */
	public DefaultAdvisorAdapterRegistry() { /* 为什么只添加这三个 */
		registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
		registerAdvisorAdapter(new AfterReturningAdviceAdapter());
		registerAdvisorAdapter(new ThrowsAdviceAdapter());
	}


	@Override
	public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
		if (adviceObject instanceof Advisor) {
			return (Advisor) adviceObject;
		}
		if (!(adviceObject instanceof Advice)) {
			throw new UnknownAdviceTypeException(adviceObject);
		}
		Advice advice = (Advice) adviceObject;
		if (advice instanceof MethodInterceptor) {
			// So well-known it doesn't even need an adapter.
			return new DefaultPointcutAdvisor(advice);
		}
		for (AdvisorAdapter adapter : this.adapters) {
			// Check that it is supported.
			if (adapter.supportsAdvice(advice)) {
				return new DefaultPointcutAdvisor(advice);
			}
		}
		throw new UnknownAdviceTypeException(advice);
	}

	@Override
	public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
		List<MethodInterceptor> interceptors = new ArrayList<>(3);
		Advice advice = advisor.getAdvice();
		/*
		* 获取到当前 Advisor中的 Advice，看看这个Advice是什么类型
		*
		* 如果 Advice 是 MethodInterceptor 类型  被添加到 interceptors 中待返回
		*
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
		*
		* */
		if (advice instanceof MethodInterceptor) {
			interceptors.add((MethodInterceptor) advice);
		}
		for (AdvisorAdapter adapter : this.adapters) {
			if (adapter.supportsAdvice(advice)) {
				interceptors.add(adapter.getInterceptor(advisor));
			}
		}
		if (interceptors.isEmpty()) {
			throw new UnknownAdviceTypeException(advisor.getAdvice());
		}
		return interceptors.toArray(new MethodInterceptor[0]);
	}

	@Override
	public void registerAdvisorAdapter(AdvisorAdapter adapter) {
		this.adapters.add(adapter);
	}

}
