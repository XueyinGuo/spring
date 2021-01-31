package com.szu.spring;

import com.szu.spring.myFactoryBean.MyFactoryBean;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {
	public static void main(String[] args) throws Exception {
//		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("gxy.xml");
//		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("star.xml");
		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("factoryBean.xml");
//		ClassPathXmlApplicationContext classPathXmlApplicationContext = new MyClassPathXmlApplicationContext("tx_${username}.xml");
//		Person person =  classPathXmlApplicationContext.getBean(Person.class);
//		System.out.println(person.getLovePerson());
//		System.out.println(person.getUserName() );

		MyFactoryBean bean = (MyFactoryBean) classPathXmlApplicationContext.getBean("&myFactoryBean");
		System.out.println(bean);

		Person person = (Person) classPathXmlApplicationContext.getBean("myFactoryBean");
		System.out.println(person.getEmail());
//		Person person1 = bean.getObject();
//		Person person11 = (Person) classPathXmlApplicationContext.getBean("person1");
//		System.out.println(person11.getId());
	}


}
