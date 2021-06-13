package com.szu.util;
/*
 * @Author 郭学胤
 * @University 深圳大学
 * @Description
 * @Date 2021/6/13 21:12
 */

import com.szu.bean.User;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;

public class BeanWrapperTest {

	public static void main(String[] args) {
		User user = new User();
		/*
		* BeanWrapper 就是一个包装类，让我们方便操作 Bean中的属性值
		* */
		BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(user);
		beanWrapper.setPropertyValue("name", "Yanni");
		System.out.println(user.getName());

		PropertyValue propertyValue = new PropertyValue("name", "star");
		beanWrapper.setPropertyValue(propertyValue);
		System.out.println(user.getName());
	}

}
