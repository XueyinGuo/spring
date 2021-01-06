package com.sztu.spring.myPrePostAutoValue;

import com.sztu.spring.myLookupMethod.Apple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;


@Component
public class PrePostAutoValue {

	@Autowired
	private Apple apple;

	public PrePostAutoValue() {
	}

	@PostConstruct
	public void init(){
		System.out.println(" *************************** ");
		System.out.println(" PrePostAutoValue -> init ");
		System.out.println(" *************************** ");
	}

	@PreDestroy
	public void destroy(){
		System.out.println(" *************************** ");
		System.out.println(" PrePostAutoValue -> destroy ");
		System.out.println(" *************************** ");
	}

}
