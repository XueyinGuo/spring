package com.szu.spring.myFactoryBean;

import com.szu.spring.Person;
import org.springframework.beans.factory.FactoryBean;

public class MyFactoryBean implements FactoryBean<Person> {
	@Override
	public Person getObject() throws Exception {
		return new Person("yanni", "The Falling Star", "1");
	}

	@Override
	public Class<?> getObjectType() {
		return Person.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}
