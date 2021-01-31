package com.szu.spring.factoryMethod;

public class PersonStaticFactory {

	public static Person getPerson(String name){
		Person person = new Person();
		person.setId(1);
		person.setName(name);
		return person;
	}
}
