package com.szu.spring;

import com.szu.spring.myFactoryBean.MyFactoryBean;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {
	public static void main(String[] args) throws Exception {
		new MyClassPathXmlApplicationContext("tx.xml");
//		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("tx.xml");
	}


}
