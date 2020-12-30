package com.sztu.spring.supplier;

/*
* 本来我们可以直接反射拿到该对象，但是用Supplier的话需要在BeanFactoryPostProcessor中做一些操作,
* 操作的BeanDefinition，
* BeanDefinition有两个主要的实现子类： GenericBeanDefinition， RootBeanDefinition
*
* GenericBeanDefinition 继承了抽象类 AbstractBeanDefinition， 抽象类中有直接设置Supplier的方法，
* 所以Bean标签没转换成RootBeanDefinition之前就可以直接设置 Supplier
* */
public class User {

	private String userName;

	public User() {
	}

	public User(String userName) {
		this.userName = userName;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	@Override
	public String toString() {
		return "User{" +
				"userName='" + userName + '\'' +
				'}';
	}
}
