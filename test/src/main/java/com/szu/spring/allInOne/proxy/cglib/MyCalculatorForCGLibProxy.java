package com.szu.spring.allInOne.proxy.cglib;


public class MyCalculatorForCGLibProxy {


	public int add(int i, int j) {
		return i + j;
	}


	public int sub(int i, int j) {
		return i - j;
	}


	public int mul(int i, int j) {
		return i * j;
	}


	public int div(int i, int j) {
		return i / j;
	}
}
