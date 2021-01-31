package com.szu.spring.myselfargs;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class PersonNamespaceHandler extends NamespaceHandlerSupport {
	@Override
	public void init() {
		registerBeanDefinitionParser("gxy", new PersonBeanDefinitionParser());
	}
}
