package com.szu.spring.aopTest;

public class MyCalculator {

	public Integer add(Integer i, Integer j) throws NoSuchMethodException{
		return i + j;
	}

	public Integer sub(Integer i, Integer j) throws NoSuchMethodException{
		return i - j;
	}

	public Integer mul(Integer i, Integer j) throws NoSuchMethodException{
		return i * j;
	}

	public Integer div(Integer i, Integer j) throws NoSuchMethodException{
		return i / j;
	}

	public Integer show(Integer i){
		System.out.println("--------show-------");
		return i;
	}
}
