package com.szu.spring.myLookupMethod;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/*
* Spring 中默认对象都是单例的， Spring会在一级缓存中持有该对象，方便下次直接获取
* 如果对象是在原型作用域，则Spring不会创建缓存该对象，而每次都要创建新的对象
* 如果想在一个单例模式的Bean中 引用一个原型模式的Bean， 怎么办？
*
* 这种情况下，就需要用lookup-method 标签来解决这个问题
*
* 通过拦截器的方式每次需要的时候都去创建最新的对象，而不会把原型对象缓存起来
* */
public class TestLookupOverride {
	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("overrideTest.xml");
		FruitPlate fruitPlate2 = (FruitPlate) ac.getBean("fruitPlate2");
		fruitPlate2.getFruit();
		FruitPlate fruitPlate1 = (FruitPlate) ac.getBean("fruitPlate1");
		fruitPlate1.getFruit();
	}
}
