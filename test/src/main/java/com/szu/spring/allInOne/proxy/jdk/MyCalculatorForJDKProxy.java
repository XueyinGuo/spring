package com.szu.spring.allInOne.proxy.jdk;


import com.szu.spring.proxy.jdk.CalculatorForJDKProxy;

public class MyCalculatorForJDKProxy implements CalculatorForJDKProxy {

	@Override
	public int add(int i, int j) {
		return i + j;
	}

	@Override
	public int sub(int i, int j) {
		return i - j;
	}

	@Override
	public int mul(int i, int j) {
		return i * j;
	}

	@Override
	public int div(int i, int j) {
		return i / j;
	}
}
