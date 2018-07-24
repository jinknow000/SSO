package com.taotao.sso.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**打开注册界面
 *jsp下的页面无法直接访问  由controller跳转到登陆、注册页面
 * @author qh_jin
 *
 */
@Controller
@RequestMapping("/page")
public class PageController {

	@RequestMapping("/register")
	//不加responsebody  就是逻辑视图
	public String showRegister() {
		return "register";
	}
	@RequestMapping("/login")
	public String showLogin(String redirect, Model model) {
		model.addAttribute("redirect", redirect);
		return "login";
//http://localhost:8084/page/login?redirect=http://www.baidu.com  必须加http://
	}
}
