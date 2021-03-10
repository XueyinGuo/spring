package com.szu.spring.allInOne.proxy.jdk;

import com.szu.spring.proxy.jdk.CalculatorForJDKProxy;
import com.szu.spring.allInOne.proxy.jdk.MyCalculatorForJDKProxy;

public class TestJDKProxy {

	public static void main(String[] args) {

		System.getProperties().put("jdk.proxy.ProxyGenerator.saveGeneratedFiles", "true");
		System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
		Object proxy = CalculatorProxy.getProxy(new MyCalculatorForJDKProxy());
		if (proxy instanceof CalculatorForJDKProxy){
			CalculatorForJDKProxy calculator = (CalculatorForJDKProxy) proxy;
			int add = calculator.add(1, 1);
			System.out.println(add);
		}

	}
}
