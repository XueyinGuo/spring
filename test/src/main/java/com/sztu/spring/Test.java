package com.sztu.spring;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("gxy.xml");
//		ClassPathXmlApplicationContext classPathXmlApplicationContext = new MyClassPathXmlApplicationContext("tx_${username}.xml");
		Person bean =  classPathXmlApplicationContext.getBean(Person.class);
		System.out.println(bean.getLovePerson());
	}


}
