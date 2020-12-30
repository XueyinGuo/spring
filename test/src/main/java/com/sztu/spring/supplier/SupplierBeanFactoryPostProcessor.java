package com.sztu.spring.supplier;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;

public class SupplierBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		BeanDefinition userBeanDefinition = beanFactory.getBeanDefinition("user");
		/*
		 * 本来我们可以直接反射拿到该对象，但是用Supplier的话需要在BeanFactoryPostProcessor中做一些操作,
		 * 操作的BeanDefinition，
		 * BeanDefinition有两个主要的实现子类： GenericBeanDefinition， RootBeanDefinition
		 *
		 * GenericBeanDefinition 继承了抽象类 AbstractBeanDefinition， 抽象类中有直接设置Supplier的方法，
		 * 所以Bean标签没转换成RootBeanDefinition之前就可以直接设置 Supplier
		 * */
		GenericBeanDefinition userGenericBeanDefinition = (GenericBeanDefinition) userBeanDefinition;
		userGenericBeanDefinition.setInstanceSupplier(CreateSupplier::createUser);
		userGenericBeanDefinition.setBeanClass(User.class);
	}
}
