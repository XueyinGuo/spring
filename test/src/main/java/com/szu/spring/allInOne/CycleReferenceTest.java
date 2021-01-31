package com.szu.spring.allInOne;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CycleReferenceTest {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("cycle.xml");
	}
}
