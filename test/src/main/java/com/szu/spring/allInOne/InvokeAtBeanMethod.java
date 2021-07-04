package com.szu.spring.allInOne;
/*
 * @Author 郭学胤
 * @University 深圳大学
 * @Description
 * @Date 2021/7/4 21:14
 */

import com.szu.spring.myConverter.People;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
public class InvokeAtBeanMethod {

	@Bean
	public People methodCanBeABeanName(){
		return new People();
	}

}
