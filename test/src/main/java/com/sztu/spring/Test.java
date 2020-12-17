package com.sztu.spring;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("tx_${username}.xml");
		Person bean =  classPathXmlApplicationContext.getBean(Person.class);
		System.out.println(bean.getName());
	}
}
