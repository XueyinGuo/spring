package com.szu.spring.allInOne;

import com.szu.spring.factoryMethod.Person;
import com.szu.spring.myFactoryBean.MyFactoryBean;
import com.szu.spring.myLookupMethod.FruitPlate;
import com.szu.spring.MyClassPathXmlApplicationContext;

public class IocXMLTest {
	public static void main(String[] args) {
//		MyClassPathXmlApplicationContext m1 = new MyClassPathXmlApplicationContext("tx_${username}.xml");
//		MyClassPathXmlApplicationContext m2 = new MyClassPathXmlApplicationContext(new String[]{"AllInOne.xml", "beforeInstantiation.xml"});
		MyClassPathXmlApplicationContext m3 = new MyClassPathXmlApplicationContext("AllInOne.xml");
		try {
			FruitPlate fruitPlate2 = (FruitPlate) m3.getBean("fruitPlate2");//单例 引用 原型
			fruitPlate2.getFruit();
			FruitPlate fruitPlate1 = (FruitPlate)m3.getBean("fruitPlate1"); //单例 引用 单例
			Object personStaticFactory = m3.getBean("personStaticFactory");
			m3.getBean("personStaticFactory", Person.class);// 静态工厂的
			m3.getBean("personInstanceFactory");// 实例工厂

//			MyFactoryBean myFactoryBean = (MyFactoryBean) m3.getBean("myFactoryBean");
			MyFactoryBean bean = (MyFactoryBean) m3.getBean("&myFactoryBean");
			System.out.println(bean);
			com.szu.spring.Person person = (com.szu.spring.Person) m3.getBean("myFactoryBean");

		}catch (Exception e){
			System.out.println(e.getLocalizedMessage());
		}

	}
}
