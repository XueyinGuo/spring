package com.szu.spring.myConverter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

public class StudentConverter implements ConverterFactory<Human, Student>{


	@Override
	public <T extends Student> Converter<Human, T> getConverter(Class<T> targetType) {
		return null;
	}
}
