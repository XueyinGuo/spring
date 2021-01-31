package com.szu.spring.proxy.jdk;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class CalculatorProxy {
	public static Object getProxy(final CalculatorForJDKProxy calculator){
		ClassLoader classLoader = calculator.getClass().getClassLoader();
		Class<?>[] interfaces = calculator.getClass().getInterfaces();
		InvocationHandler invocationHandler = new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				Object result = null;
				try{
					result = method.invoke(calculator, args);
				}catch (Exception e){
					System.out.println(e.getStackTrace());
				}finally {

				}
				return result;


			}
		};
		Object o = Proxy.newProxyInstance(classLoader, interfaces, invocationHandler);
		return o;
	}
}
