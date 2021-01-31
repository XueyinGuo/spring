package com.szu.spring.txTest.annotation;

import com.szu.spring.myRegisterEditor.Star;
import com.szu.spring.Person;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfiguration {

	@Bean
	public Person person(){
		return new Person();
	}

	@Bean
	public Single single(){
		person();
		return new Single();
	}
}
