package com.sztu.spring.populateBean;


import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestPopulate {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("populateBean.xml");
	}
}
