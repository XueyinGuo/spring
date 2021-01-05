package com.sztu.spring.myBeanFactoryPostProcessor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

	private String name;

	public MyBeanFactoryPostProcessor(String name) {
		this.name = name;
		System.out.println("Yanni can't breathe at all without The Falling Star!");
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

	}

	public MyBeanFactoryPostProcessor() {
	}
}
