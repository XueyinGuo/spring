package com.szu.spring.myselfargs;

import com.szu.spring.Person;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

public class PersonBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	@Override
	protected Class<?> getBeanClass(Element element) {
		return Person.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		String userName = element.getAttribute("userName");
		String lovePerson = element.getAttribute("lovePerson");
		String id = element.getAttribute("id");

		if (StringUtils.hasText(userName)){
			builder.addPropertyValue("userName", userName);
		}
		if (StringUtils.hasText(lovePerson)){
			builder.addPropertyValue("lovePerson", lovePerson);
		}
		if (StringUtils.hasText(id)){
			builder.addPropertyValue("id", id);
		}
	}
}
