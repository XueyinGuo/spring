package com.sztu.spring.proxy.cglib;

import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.cglib.proxy.Enhancer;

public class TestCGLibProxy {
	public static void main(String[] args) {
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "d:\\1cglib");
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(MyCalculatorForCGLibProxy.class);
		enhancer.setCallback(new MyCGLib());
		MyCalculatorForCGLibProxy o = (MyCalculatorForCGLibProxy) enhancer.create();
		int add = o.add(1, 1);
		System.out.println(add);
	}
}
/*
* 1. 先创建一个 enhancer 对象
* */