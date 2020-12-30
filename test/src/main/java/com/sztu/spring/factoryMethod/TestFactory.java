package com.sztu.spring.factoryMethod;

import com.sztu.spring.supplier.User;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestFactory {
	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("factoryMethod.xml");
		Person person = ac.getBean("person",Person.class);
		Person person2 = ac.getBean("person2",Person.class);
		System.out.println(person+ "  "+ person2);
	}

}
