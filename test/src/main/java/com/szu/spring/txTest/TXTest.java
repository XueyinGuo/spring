package com.szu.spring.txTest;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TXTest {
	public static void main(String[] args) {
		/*
		* 在普通AOP中，有几个需要注意的对象， Advice#0、advisor、pointcut、MethodInterceptor
		* 在事务中的几个相对应的对象也差不多是这总类型，但是名字有稍微一些变化。
		*
		* AspectJPointcutAdvisor 变成了   DefaultBeanFactoryPointcutAdvisor
		* MethodInterceptor 变成了 TransactionInterceptor，而且 TransactionInterceptor 实现了 Advice接口，可以直接当成 Advice对象使用
		* 							TransactionInterceptor 中嵌套了 NameMatchTransactionAttributeSource，其中都是 method属性
		*
		*
		* 	<aop:config>
				<aop:pointcut id="txPoint" expression="execution(* com.szu.spring.txTest.*.*(..))"/>
				<aop:advisor advice-ref="myAdvice" pointcut-ref="txPoint"></aop:advisor>
			</aop:config>

			<tx:advice id="myAdvice" transaction-manager="transactionManager">
				<tx:attributes>
					<tx:method name="*"/>
					<tx:method name="checkout" propagation="REQUIRED"></tx:method>
					<tx:method name="get*" read-only="true"/>
				</tx:attributes>
			</tx:advice>

		* ==========================================================================================================================
		* ==========================================================================================================================
			 																				  														    { <tx:method
			 包含关系如下所示 DefaultBeanFactoryPointcutAdvisor --->  TransactionInterceptor (实现了 Advice 接口)  --->  NameMatchTransactionAttributeSource { <tx:method
 			 								|									|			  				                       |                	{ <tx:method
 			 								|									|			  				                       |
 			 								|									|			  				                       |
 	（注解配置方式的事务通知器类）BeanFactoryTransactionAttributeSourceAdvisor      TransactionInterceptor				AnnotationTransactionAttributeSource


		* pointcut 依然是  AspectJExpressionPointcut
		*
		*
		* ！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
		* ！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
		* 在事务中的 Advisor 就只有两个， 一个 ExposeInvocationInterceptor    责任链的连接
		* 							 一个 DefaultBeanFactoryPointcutAdvisor 包装 TransactionInterceptor
		* ！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
		* ！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
		* ==========================================================================================================================
		* ==========================================================================================================================
		*	事务的传播特性
		*
		*
		*													|-----------REQUIRED --->  使用当前事务，如果没有，创建一个事务
		*													|
		*													|
		*													|
		*													|------------SUPPORTS ---> 如果有，就使用当前事务，如果没有就不用事务了
		*													|
		*													|
		*									【支持外层事务】	|------------MANDATORY --> 使用当前事务，如果没有事务就直接抛出异常
		*													|
		* 如果外层方法中包含事务，那么内层方法是否需要支持当前事务 ---- **************************************************************
		*													|
		*									【不支持外层事务】	|------------REQUIRED_NEW -> 如果存在事务，将事务挂起，自己新建一个事务
		*													|
		*													|
		*													|------------NOT_SUPPORTED -> 不需要使用事务，如果存在就挂起外层事务
		*													|
		*													|
		*													|------------NEVER ----------> 不需要事务，如果存在事务就抛出异常
		*
		*
		* 		NESTED  --->  如果有事务正在运行，当前的方法就应该在这个事务内部创建一个新的事务，否则启动一个新事务，并在自己的事务内运行
		*
		* ===============================================================================================================
		* ===============================================================================================================
		*
		* 事务执行流程：
		* 	1. 创建一个基本的事务
		* 	2.执行事务操作，执行SQL语句（是否有异常）
		* 	3.
		* 		3.1 有异常的话 ： 清除事务信息，恢复之前的事务信息， rollback ，最终也是依托数据库事务
		* 		3.2 没有异常 ： 事务正常执行之后，清除事务信息（TransactionInfo），然后 commit提交事务，最终依托数据库事务
		* 	4. 当前的事务已经结束
		* 		所以我们要释放连接，关闭连接
		* ===============================================================================================================
		* ===============================================================================================================
		* 我们的事务应该在哪里嵌入进去呢？
		* 再把纯AOP拿出来看一下，他有五种消息通知：  after before afterReturning afterThrowing around
		*
		* 	所以很容易想到
		* 			1. around 或者 (before + after) 来类比方法执行的前置后置处理
		* 			2. afterReturning 类比 可以用于返回正常额执行结果
		* 			3. afterThrowing 可以处理 执行SQL异常的情况
		*
		* 但事务不是这样来搞得，其实就是把这个东西简化了，毕竟只有两个Advisor
		*
		* ===============================================================================================================
		* ===============================================================================================================
		*
		* 事务开始执行的前后：
		* 		1. 准备事务处理相关的对象：事务对象，连接器，事务信息，事无状态，事务属性
		*
		* 													  {	1. 准备事务处理相关的对象：事务对象，连接器，事务信息，事无状态，事务属性
		* 													  {
		* 		2. 执行事务 ----嵌套事务（事务的传播特性控制）-----> {	2. 执行事务 ----嵌套事务----->
		* 													  {
		* 													  {	3.是否有异常： 回滚或者正常提交
		*
		* 		3.是否有异常： 回滚或者正常提交
		*
		* */



		/*
		* 传播特性对事务的影响：
		* 	1. Service 中 REQUIRED， DAO 中 REQUIRED，
		* 		内外公用一个事务，内层异常会设置一个“全局回滚标记”，由于内层不是一个新创建的事务，所以内层抛出异常也不会 执行
		* 		doRollBack() ,只设置一个回滚标记为 true，
		* 		内层执行完恢复外层现场之后，如果外层方法：
		* 					（1）如果外层方法捕获异常：执行提交的时候先检查全局回滚标记，外层是一个新事务，所以 doRollBack()
		* 					（2）外层没有捕获也是向外抛出，那么外层事务不会提交，会直接执行 doRollBack()
		*
		 			<tx:method name="checkout" propagation="REQUIRED">
					<tx:method name="get*" propagation="REQUIRED"/>


		*
		*  	2.  Service 中 REQUIRED， DAO 中 NESTED
		*			NESTED创建完保存点之后，直接返回 NESTED 旧的事务信息（没有连接创建新连接，也还是使用的之前的事务）
		*
		*			（1）当DAO中抛出异常之后，直接回滚到保存点之后，重置回滚标记，之后就不需要回滚了。
		* 					（如果内层方法有自己的异常捕获，那就连回滚到保存点都不用了，就可以直接都提交了）
		*
		* 			（2）如果外层事务没有自己的异常，外层Service中的事务就可以提交了
		*
							<tx:method name="checkout" propagation="REQUIRED">
							<tx:method name="get*" propagation="NESTED"/>


		*
		*
		* 	3. Service 中 REQUIRED， DAO 中 REQUIRED_NEW
		*
		* 			外层Service事务创建好之后，内层 REQUIRED_NEW 会启用一个新的事务，把旧事物挂起。
		* 				挂起就是说把老事务所有的值保存下来之后，放入一个 挂起资源持有器 中，内层事务重新设置这个事务（相当于重新开启了一个事务）
		* 				此时内外层事务是独立的。
		* 			当内层事务处理好之后，跟外层事务一样绑定资源各种操作，因为内层事务可能嵌套更深的内内层 其他传播特性的事务。
		*
		* 			假设内层抛出异常，内层是一个新事务，可以直接执行 doRollBack()，执行完之后 恢复老事务现场，
		* 			执行	resume() 方法， 里边重新调用 bindResource方法，重新绑定回去。
		* 			service老事务 中可能调用其他 DAO层 方法，然后开始其他的传播特性的各种工作。
		*
		* 			（1）外层事务可能回受到内层事务的影响，因为内层在恢复完老事务现场之后，还会将异常继续上抛出，
		* 					所以如果在service中我们没有捕获异常，service中仍然会受到内层事务的异常通知，从而影响外层事务。
					（2）内层无异常，内层无异常，内层还是会提交，
							所以结论就是外层不会影响内层，内层会影响外层

		 					<tx:method name="checkout" propagation="REQUIRED">
							<tx:method name="get*" propagation="NESTED"/>

		*  */


		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("transaction.xml");
		BookService bean = ac.getBean(BookService.class);
		bean.checkout("zhangsan", 1);
	}
}
