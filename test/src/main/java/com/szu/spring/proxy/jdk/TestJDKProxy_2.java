package com.szu.spring.proxy.jdk;


public class TestJDKProxy_2 {

	public static void main(String[] args) {

		System.getProperties().put("jdk.proxy.ProxyGenerator.saveGeneratedFiles", "true");
		System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
		Object proxy = new com.szu.spring.proxy.jdk.CalculatorProxy_2().getProxy(new com.szu.spring.allInOne.proxy.jdk.MyCalculatorForJDKProxy());
		if (proxy instanceof com.szu.spring.proxy.jdk.CalculatorForJDKProxy){
			com.szu.spring.proxy.jdk.CalculatorForJDKProxy calculator = (com.szu.spring.proxy.jdk.CalculatorForJDKProxy) proxy;
			int add = calculator.add(1, 1);
			System.out.println(add);
		}

	}
}
