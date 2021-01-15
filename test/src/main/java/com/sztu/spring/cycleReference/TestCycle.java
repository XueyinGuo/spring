package com.sztu.spring.cycleReference;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestCycle {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("cycle.xml");

	}
}
