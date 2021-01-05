package com.sztu.spring.myCustomEditor;

import java.beans.PropertyEditorSupport;

public class MyCustomEditor extends PropertyEditorSupport {

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		super.setAsText(text);
	}
}
