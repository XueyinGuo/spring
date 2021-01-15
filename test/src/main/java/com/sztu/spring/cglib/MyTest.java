package com.sztu.spring.cglib;

import com.sztu.spring.aopTest.MyCalculator;
import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.cglib.proxy.Enhancer;

public class MyTest {
	public static void main(String[] args) throws NoSuchMethodException {
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "d:\\code");
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(MyCalculator.class);
		enhancer.setCallback(new MyCglib());
		MyCalculator o = (MyCalculator) enhancer.create();
		Integer add = o.add(1, 1);
		System.out.println(o.getClass());
	}
}
