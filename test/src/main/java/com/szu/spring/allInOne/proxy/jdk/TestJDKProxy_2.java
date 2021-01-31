package com.szu.spring.proxy.jdk;

public class TestJDKProxy_2 {

	public static void main(String[] args) {

		System.getProperties().put("jdk.proxy.ProxyGenerator.saveGeneratedFiles", "true");
		System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
		Object proxy = new CalculatorProxy_2().getProxy(new MyCalculatorForJDKProxy());
		if (proxy instanceof CalculatorForJDKProxy){
			CalculatorForJDKProxy calculator = (CalculatorForJDKProxy) proxy;
			int add = calculator.add(1, 1);
			System.out.println(add);
		}

	}
}
