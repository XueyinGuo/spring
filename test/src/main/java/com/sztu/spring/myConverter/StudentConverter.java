package com.sztu.spring.myConverter;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.core.convert.converter.GenericConverter;

import java.util.Set;

public class StudentConverter implements ConverterFactory<Human, Student>{


	@Override
	public <T extends Student> Converter<Human, T> getConverter(Class<T> targetType) {
		return null;
	}
}
