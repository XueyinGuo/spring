package com.sztu.spring.resolveBeforeInstantiation;

import com.sztu.spring.myLookupMethod.Banana;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.cglib.proxy.Enhancer;
/*
* 给BeanPostProcessor一个机会返回一个代理来替代真正的实例
* 如果代理的bean不为空，则直接返回代理bean
* 往下的 doCreateBean 也不用执行了
* */
public class MyInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

	/*
	* 实例化之前
	* */
	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcess ==Before== Instantiation");
		if (beanClass == BeforeInstantiation.class){
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(beanClass);
			enhancer.setCallback(new MyMethodInterceptor());
			BeforeInstantiation beforeInstantiation = (BeforeInstantiation) enhancer.create();
			System.out.println("创建代理对象 ：" + beforeInstantiation);
			return beforeInstantiation;
		}
		return null;
	}

	/*
	* 实例化之后
	* */
	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcess ==After== Instantiation");
		/*
		 * 在 populateBean(beanName, mbd, instanceWrapper); 中填充属性的时候
		 * 如果自己实现了 InstantiationAwareBeanPostProcessor 接口中，自己重写了 postProcessAfterInstantiation方法
		 * 在方法中给这个 类型的 bean 赋值，而且不希望自己被赋的值被下边的操作覆盖掉，所以就要在
		 * postProcessAfterInstantiation方法 中返回 false，  ！false = true 直接跳出当前 populateBean方法执行
		 * */
		if(bean instanceof Banana){

			return false;
		}
		return true;
	}
	/*
	* 初始化之前
	* */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcess ==Before== Initialization");
		return bean;
	}
	/*
	* 初始化之后
	* */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcess ==After== Initialization");
		return bean;
	}

	/*
	* 对值进行操作
	* */
	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
		System.out.println("BeanName: "+beanName + "  执行MyInstantiationAwareBeanPostProcessor -> postProcessProperties");
		return pvs;
	}
}
