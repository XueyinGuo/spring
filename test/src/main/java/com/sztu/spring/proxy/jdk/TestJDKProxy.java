package com.sztu.spring.proxy.jdk;

import com.sztu.spring.aopTest.Calculator;
import com.sztu.spring.aopTest.MyCalculator;
import sun.security.action.GetBooleanAction;

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
