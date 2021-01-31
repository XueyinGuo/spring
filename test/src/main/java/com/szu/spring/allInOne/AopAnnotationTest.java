package com.szu.spring.allInOne;

import com.szu.spring.aopTest.annotation.MyCalculatorForAopAnnotationTest;
import com.szu.spring.aopTest.annotation.SpringConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class AopAnnotationTest {
	public static void main(String[] args) throws NoSuchMethodException {
		/*
		 * 1. new StandardEnvironment();
		 * 2. <context:component-scan>的以internal为开头的BeanDefinition注入到 BeanFactory
		 * */
		/*
		 * 1. 把刚刚创建的工厂也赋值给 这个对象中的属性
		 * 2. setEnvironment(environment);
		 * */
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
		/*
		* /* 把配置类也加载成一个 BeanDefinition 放入到 IOC容器
		* */
		annotationConfigApplicationContext.register(SpringConfiguration.class);
		annotationConfigApplicationContext.refresh();
		MyCalculatorForAopAnnotationTest bean = annotationConfigApplicationContext.getBean(MyCalculatorForAopAnnotationTest.class);
		/*
		* 注解方式 和 XML文件 的 链条执行逻辑不太一样，是因为拓扑排序的时候给 Advisor 排序的时候，顺序稍微不一样而已，反正不影响执行结果就行
		*
		*  		|---> B --> |
		*  A ---|    		| ---> D  这个拓扑排序一样，有两种结果：   ABCD 和 ACBD！！！
		* 		|---> C --> |
		*
		* 不影响执行结果就行了
		*
		*
		* */
		Integer add = bean.add(1, 1);
		System.out.println(add);
	}
}
