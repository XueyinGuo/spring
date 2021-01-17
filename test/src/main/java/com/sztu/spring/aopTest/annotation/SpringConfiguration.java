package com.sztu.spring.aopTest.annotation;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configurable
@ComponentScan(basePackages = "com.sztu.spring.aopTest.annotation")
@EnableAspectJAutoProxy
public class SpringConfiguration {
}
