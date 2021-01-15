package com.sztu.spring.proxy.cglib;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

public class MyCGLib implements MethodInterceptor /**/ {

	@Override
	public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
		Object o1 = methodProxy.invokeSuper(o, objects);
		return o1;
	}
}
