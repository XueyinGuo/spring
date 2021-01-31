package com.szu.spring.txTest.annotation;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TXAnnotationTest {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
		annotationConfigApplicationContext.register(TransactionConfig.class);
		/*
		* @Configuration 注解修饰的类，在纯Spring项目中需要 register
		* */
		annotationConfigApplicationContext.register(MyConfiguration.class);
		annotationConfigApplicationContext.refresh();
		AnnotationBookService bean = annotationConfigApplicationContext.getBean(AnnotationBookService.class);
		bean.checkout("zhangsan", 1);
	}
}
