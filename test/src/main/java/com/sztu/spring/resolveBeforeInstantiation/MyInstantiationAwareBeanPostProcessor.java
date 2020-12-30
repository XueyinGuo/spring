package com.sztu.spring.resolveBeforeInstantiation;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.cglib.proxy.Enhancer;

public class MyInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

	/*
	* 实例化之前
	* */
	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcess ==Before== Instantiation");
		if (beanClass == BeforeInstantiation.class){
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(beanClass);
			enhancer.setCallback(new MyMethodInterceptor());
			BeforeInstantiation beforeInstantiation = (BeforeInstantiation) enhancer.create();
			System.out.println("创建代理对象 ：" + beforeInstantiation);
			return beforeInstantiation;
		}
		return null;
	}

	/*
	* 实例化之后
	* */
	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcess ==After== Instantiation");
		return false;
	}
	/*
	* 初始化之前
	* */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcess ==Before== Initialization");
		return bean;
	}
	/*
	* 初始化之后
	* */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcess ==After== Initialization");
		return bean;
	}

	/*
	* 对值进行操作
	* */
	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcessProperties");
		return pvs;
	}
}
