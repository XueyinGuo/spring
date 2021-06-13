package com.szu.controller;/*
 * @Author 郭学胤
 * @University 深圳大学
 * @Description
 * @Date 2021/6/13 18:38
 */

import com.szu.bean.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HelloController {

	@RequestMapping("sayHello")
	@ResponseBody
	public ModelAndView sayHello(){
		ModelAndView modelAndView = new ModelAndView();
		User user = new User();
		user.setName("Yanni");
		user.setAge(25);
		modelAndView.setViewName("sayHello");
		return modelAndView;
	}

}
