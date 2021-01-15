package com.sztu.spring.cycleReference;

public class B {
	private A a;

	public A getA() {
		return a;
	}

	public void setA(A a) {
		this.a = a;
	}

//	private C c;
//
//	public C getC() {
//		return c;
//	}
//
//	public void setC(C c) {
//		this.c = c;
//	}
}
