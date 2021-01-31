package com.szu.spring.allInOne;

import com.szu.spring.aopTest.MyCalculator;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/*
* 这是 额外添加的业务逻辑类
*
* 类中都是额外的业务逻辑，那我们怎么知道在什么时候调用这些额外的业务逻辑呢？
*
*
* <bean id="logUtil" class="com.szu.spring.aopTest.LogUtil"></bean>

	<bean id="myCalculator" class="com.szu.spring.aopTest.MyCalculator"></bean>

	<aop:config>
		<aop:aspect ref="logUtil"> 																								 <!--指定切入逻辑类 aspect-->
			<aop:pointcut id="myPoint" expression="execution(Integer com.szu.spring.aopTest.MyCalculator.*(Integer,Integer))"/> <!--所有的连接点们  join point-->
			<aop:around method="around" pointcut-ref="myPoint"></aop:around> 													 <!--切点切入后执行的方法 切点 pointCut-->
			<aop:before method="start" pointcut-ref="myPoint"></aop:before>														 <!--切点切入后执行的方法-->
			<aop:after method="logFinally" pointcut-ref="myPoint"></aop:after>
			<aop:after-returning method="stop" pointcut-ref="myPoint" returning="result"></aop:after-returning>
			<aop:after-throwing method="logException" pointcut-ref="myPoint" throwing="e"></aop:after-throwing>
		</aop:aspect>
	</aop:config>
	*

* 我们怎么做才能做到切面切入之后执行方法呢？
* 1.先编写额外的逻辑类 （就是现在的 logUtil） 						 ---> 切面  aspect
*
*
* 2.哪些方法要被执行处理（around before after等）				     ---> 切点   pointCut  ----->  advisor(通知器)  -----> advise
*
*
* 3.在那里调用执行呢？ （expression 表达式中的所有符合规定的方法）        ---> 连接点  join point
*
*
* 需要怎么样才能完成上述步骤呢？
*
* a: 知道哪些类需要进行切入    ->  expression="execution(Integer com.szu.spring.aopTest.MyCalculator.*(Integer,Integer))
* b: 有哪些额外的方法会被执行  ->   around  before  after  after-returning  after-throwing   :   五个方法的总称叫做 advisor
* c: 哪个是额外逻辑处理类     ->   aspect
* d: 当我知道在哪些方法上处理额外的逻辑时，我必须要先得到这个方法对象，才能进行方法调用和额外的处理逻辑执行
* 		所以我们还需要 method对象
* e: 得到方法对象之后，还需要知道 这个方法是哪一个 Bean 的，但是在整个容器中我们没办法直接定位到某一个Bean对象，所以我们还需要容器对象，
* 		从容器中遍历所有的Bean对象。所以我们需要知道 BeanFactory
*
* 经过上述步骤已经准备好了AOP所需要的东西，接下来就可以进行动态代理操作了。
*
* AOP使用方式当然还是两种，仍然 注解只是对XML文件的扩展，就像 <context:component-scan>一样的处理逻辑，会使用一个类给容器添加一些internal
*
* ======================================================================================================================
* 在解析有AOP配置的XML文件
* 1.解析XML，处理成BeanDefinition
* 2.对AOP相关的 BeanDefinition 实例化操作，
* 			但是在第一个自定义的对象（比如star对象）创建之前，就必须吧AOP相关对象提前配置好，因为我们无法预估那个对象需要被动态代理
* 			那在那里先生成AOP实例化和初始化呢？  -------->>>>> 答案是 【BeanPostProcessor】
* 					=====
* 					createBean()的过程中有resolveBeforeInstantiation()，给对象一个机会返回代理对象
* 					=====
* 			【为啥不是 BeanFactoryPostProcessor 呢， 因为 BFPP 基本都是用来操作 BeanDefinition的，和进行一些BeanFactory的属性修改操作的】
*
*
* ==============================================================================================================================================
* ==============================================================================================================================================
* ==============================================================================================================================================
	一、loadBeanDefinition方法解析<aop:config>的过程
		 0. 添加一个自动代理创建器，internalAutoProxyCreator

		 1. 创建三个 RootBeanDefinition，封装给 adviceDef

			五个 adviceDef 分别对应五个Advice类  AspectJAfterAdvice AspectJAfterReturningAdvice  AspectJAfterThrowingAdvice
												AspectJAroundAdvice   AspectJMethodBeforeAdvice

			每个after before after-returning after-throwing around 标签都解析成一个 adviceDef 然后外边再套一层  Advisor

			                  		           { -----> MethodLocatingFactoryBean
				Advisor --->   adviceDef --->  { -----> expression="execution(Integer com.szu.spring.aopTest.MyCalculator.*(Integer,Integer))"
							                   { -----> SimpleBeanFactoryAwareAspectInstanceFactory

		2. 五个Advisor，和所有解析出的表达式，放入一个 aspectComponentDefinition 中


		3. 解析 point-cut ，解析出表达式的结果值
		   	pointcutsBean 的 BeanDefinition的 beanClass = AspectJExpressionPointcut
		   	把point-cut的BeanDefinition注册到BeanFactory

* ==============================================================================================================================================
* ==============================================================================================================================================
* ==============================================================================================================================================
*
* 二、那注解是怎么处理的呢？
* 	比如我们自己定义了一个类上边标了 @Component @Aspect，我们的容器怎么把这个注解标注的类扫描成 【一中说的BeanDefinition】 呢？
* 	除了打开 <context:component-scan>之外 还需要什么？
*   【答案是还需要： <aop:aspectj-autoproxy> 这个标签 】
*
*		 注册 AutoProxyCreator，名为 internalAutoProxyCreator 的 BeanDefinition
*		 beanClass 为 AnnotationAwareAspectJAutoProxyCreator
*		 AnnotationAwareAspectJAutoProxyCreator 实现了 BeanPostProcessor -> InstantiationAwareBeanPostProcessor -> SmartInstantiationAwareBeanPostProcessor
*
*		 是不是之后用这个BeanDefinition创建出来的 Bean 去扫描，标了@Component @Aspect 的Bean中的before after 方法等等操作？？？
*
* ==============================================================================================================================================
* ==============================================================================================================================================
* ==============================================================================================================================================
*
* 三、创建所有的 AOP相关的 BeanDefinition之后，所有的BeanDefinition的用处如下：
* 		1. internalAutoProxyCreator -> 代理创建器
* 		2. Advisor#0--#4            -> around before 等五个方法对应的 BeanDefinition
* 		3. myPoint                  -> 表达式的 BeanDefinition
* 		4. logUtil                  -> 切面
* 		5. myCalculator             -> 包含的连接点方法的类，执行这些满足 myPoint(表达式规则) 的方法时 去 logUtil（切面）找到合适的 Advisor#0--#4 方法执行
* */
public class AopXMLTest {
	public static void main(String[] args) throws NoSuchMethodException {
		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("aop.xml");
		MyCalculator bean = classPathXmlApplicationContext.getBean(MyCalculator.class);
		/*
		* 跳入 DynamicAdvisedInterceptor 中进行执行
		*
		* ===================================================================================================================
		* ===================================================================================================================
		* 当生成代理之后就可以进行方法调用了，但是此时共有6个 Advisor，他们在执行的时候按照某个顺序来执行，而且由一个通知会跳转到另外一个通知，
		* 所以此时，我们需要构建一个拦截器链（责任链模式），只有创建好当前的链式结构，才能顺利往下进行！！！！
		*
		* 所以 你还记得给 Advisor 的排序吗？？？！！！！
		* 经过排序之后的 Advisor的顺序是
		* 		1. exposeInvocationInterceptor  ---> 根据索引的下标获取对应的通知来执行，相当于 【工作协调者】
		* 		2. afterThrowing                ---> 等方法抛出异常后续执行的逻辑
		* 		3. afterReturning				---> 方法完成执行之后后续执行的逻辑                 -> AspectJAfterReturningAdvice 没有 invoke 方法
		* 		4. after						---> 后续执行的逻辑
		* 		5. around						---> 第一个执行，在执行过程中调用 before执行
		* 		6. before						---> around 执行期间执行，执行完之后继续执行 around
		* ===================================================================================================================
		* ===================================================================================================================
		*
		* */
		Integer add = bean.add(1, 1);
		System.out.println(add);
	}
}
