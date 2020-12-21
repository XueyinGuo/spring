package com.sztu.spring;

import com.sztu.spring.myRegisterEditor.Star;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class  Test02 {
	public static void main(String[] args) {
//		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("gxy.xml");
		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("star.xml");
//		ClassPathXmlApplicationContext classPathXmlApplicationContext = new MyClassPathXmlApplicationContext("tx_${username}.xml");
		Star star =  classPathXmlApplicationContext.getBean(Star.class);
		System.out.println(star.getName());
		System.out.println(star.getAddress() );
	}


}
