package com.sztu.spring.myRegisterEditor;

import org.springframework.util.StringUtils;

import java.beans.PropertyEditorSupport;

public class MyResourceEditor extends PropertyEditorSupport {

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		if (StringUtils.hasText(text)){
			String[] s = text.split("_");
			Address address = new Address();
			address.setProvince(s[0]);
			address.setCity(s[1]);
			address.setTown(s[2]);
			address.setCommunity(s[3]);
			address.setBuilding(s[4]);
			setValue(address);
		}
	}
}
