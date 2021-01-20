package com.sztu.spring.txTest;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TXTest {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("transaction.xml");
		BookService bean = ac.getBean(BookService.class);
		bean.checkout("zhangsan", 1);
	}
}
