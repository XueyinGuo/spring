package com.szu.bean;/*
 * @Author 郭学胤
 * @University 深圳大学
 * @Description
 * @Date 2021/6/13 17:35
 */

public class User {

	public String name;
	public int age;

	public User() {
	}

	public User(String name, int age) {
		this.name = name;
		this.age = age;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}
}
