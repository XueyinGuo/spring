package com.sztu.spring;

public class Person {
	private String userName;
	private String lovePerson;
	private String id;

	public Person() {
	}

	public Person(String userName, String lovePerson, String id) {
		this.userName = userName;
		this.lovePerson = lovePerson;
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getLovePerson() {
		return lovePerson;
	}

	public void setLovePerson(String lovePerson) {
		this.lovePerson = lovePerson;
	}
}
