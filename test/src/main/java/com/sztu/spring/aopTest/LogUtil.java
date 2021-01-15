package com.sztu.spring.aopTest;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.*;

import java.util.Arrays;

public class LogUtil {

	@Pointcut("execution(public Integer com.sztu.spring.aopTest.MyCalculator.*(Integer, Integer))")
	public void myPointCut(){}

	@Pointcut("execution(* *(..))")
	public void myPointCut1(){}

	@Before(value = "myPointCut()")
	private int start(JoinPoint joinPoint){
		Signature signature = joinPoint.getSignature();
		Object[] args = joinPoint.getArgs();
		System.out.println("log --- "+ signature.getName() + " 方法执行，参数是 --- "+ Arrays.asList(args));
		return 100;
	}

	@AfterReturning(value = "myPointCut()", returning = "result")
	public static void stop(JoinPoint joinPoint, Object result){
		Signature signature = joinPoint.getSignature();
		System.out.println("log --- "+ signature.getName() + " 方法执行结束。");
	}

	@AfterThrowing(value = "myPointCut()", throwing = "e")
	public static void logException(JoinPoint joinPoint, Exception e){
		Signature signature = joinPoint.getSignature();
		System.out.println("log --- "+ signature.getName() + " 方法执行期间爆出异常 --- " + e.getStackTrace());
	}

	@After(value = "myPointCut()")
	public static void logFinally(JoinPoint joinPoint){
		Signature signature = joinPoint.getSignature();
		System.out.println("log --- "+ signature.getName() + " 方法执行 Finally ");
	}

	@Around("myPointCut()")
	public Object around(ProceedingJoinPoint pjp) throws Throwable{
		Signature signature = pjp.getSignature();
		Object[] args = pjp.getArgs();
		Object result = null;
		try{
			System.out.println("log --- 环绕通知start： "+ signature.getName()+" 方法开始执行，参数是 "+ Arrays.asList(args));

			result = pjp.proceed(args);
			System.out.println("log --- 环绕通知end： "+ signature.getName()+" 方法开始执行结束。");
		}catch (Throwable t){
			System.out.println("log --- "+ signature.getName() + " 方法执行期间爆出异常 --- ");
			throw t;
		}finally {
			System.out.println("log --- 环绕通知返回： "+ signature.getName()+" 方法开始执行结果是" + result);
		}
		return result;
	}

}
