package com.sztu.spring;

import com.sztu.spring.myRegisterEditor.MyEditorRegistrar;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MyClassPathXmlApplicationContext extends ClassPathXmlApplicationContext {
	public MyClassPathXmlApplicationContext(String... configLocations) {
		super(configLocations);
	}

	@Override
	/*
	 * 如果我们自己扩展实现了一些 setRequiredProperties(String... str)
	 * 可以refresh()中 prepareRefresh() 中 的一个方法 getEnvironment().validateRequiredProperties();中
	 * 得到验证，如果没有设置的属性则直接抛出异常
	 * 如果非得要有一个莫名其妙的参数，可以通过 -D参数指定， 比如 -D abc=def
	 * */
	protected void initPropertySources() {
//		this.getEnvironment().setRequiredProperties("abc");

	}

	@Override
	protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
		super.setAllowBeanDefinitionOverriding(false);
		/*
		* 添加 BFPP 的一个办法
		* */
		super.addBeanFactoryPostProcessor(new MyBeanFactoryProcessor());
		super.setAllowCircularReferences(false);
		super.customizeBeanFactory(beanFactory);
	}

	@Override
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		System.out.println("This is My Own postProcessBeanFactory()");
	}
}
