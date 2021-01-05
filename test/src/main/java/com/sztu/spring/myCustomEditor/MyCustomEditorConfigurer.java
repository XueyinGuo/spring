package com.sztu.spring.myCustomEditor;

import com.sztu.spring.myRegisterEditor.Address;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.CustomEditorConfigurer;

import java.beans.PropertyEditor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MyCustomEditorConfigurer extends CustomEditorConfigurer {


	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		MyCustomEditor myCustomEditor = new MyCustomEditor();
		Map<Class<?>, Class<? extends PropertyEditor>> map = new Map<Class<?>, Class<? extends PropertyEditor>>() {
			@Override
			public int size() {
				return 0;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}

			@Override
			public boolean containsKey(Object key) {
				return false;
			}

			@Override
			public boolean containsValue(Object value) {
				return false;
			}

			@Override
			public Class<? extends PropertyEditor> get(Object key) {
				return null;
			}

			@Override
			public Class<? extends PropertyEditor> put(Class<?> key, Class<? extends PropertyEditor> value) {
				return null;
			}

			@Override
			public Class<? extends PropertyEditor> remove(Object key) {
				return null;
			}

			@Override
			public void putAll(Map<? extends Class<?>, ? extends Class<? extends PropertyEditor>> m) {

			}

			@Override
			public void clear() {

			}

			@Override
			public Set<Class<?>> keySet() {
				return null;
			}

			@Override
			public Collection<Class<? extends PropertyEditor>> values() {
				return null;
			}

			@Override
			public Set<Entry<Class<?>, Class<? extends PropertyEditor>>> entrySet() {
				return null;
			}

			@Override
			public boolean equals(Object o) {
				return false;
			}

			@Override
			public int hashCode() {
				return 0;
			}
		};
		map.put(Address.class, MyCustomEditor.class);
		this.setCustomEditors(map);
		super.postProcessBeanFactory(beanFactory);
	}
}
