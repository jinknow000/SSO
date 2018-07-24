package com.taotao.sso.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import com.taotao.common.utils.CookieUtils;
import com.taotao.common.utils.JsonUtils;
import com.taotao.common.utils.TaotaoResult;
import com.taotao.mapper.TbUserMapper;
import com.taotao.pojo.TbUser;
import com.taotao.pojo.TbUserExample;
import com.taotao.pojo.TbUserExample.Criteria;
import com.taotao.sso.dao.JedisClient;
import com.taotao.sso.service.UserService;

@Service
public class UserServiceImpl implements UserService {

	@Autowired
	private TbUserMapper userMapper;
	
	@Autowired
	private JedisClient jedisClient;
	
	@Value("${REDIS_USER_SESSION_KEY}")
	private String REDIS_USER_SESSION_KEY;
	@Value("${SSO_SESSION_EXPIRE}")
	private Integer SSO_SESSION_EXPIRE;
	
	/**
	 * 用户名、电话、邮箱的校验
	 */
	@Override
	public TaotaoResult checkData(String content, Integer type) {
		//
		TbUserExample example = new TbUserExample();
		Criteria criteria = example.createCriteria();
	
		// 校验的数据，type为类型，可选参数1、2、3分别代表username、phone、email
		if(1 == type) {
			criteria.andUsernameEqualTo(content);
		}else if(2 == type) {
			criteria.andPhoneEqualTo(content);
		}else {
			criteria.andEmailEqualTo(content);
		}
		//执行查询
		List<TbUser> list = userMapper.selectByExample(example);
		if(list == null || list.size() == 0) {
			return TaotaoResult.ok(true);
		}
		return TaotaoResult.ok(false);
	}

	/**
	 * 用户注册
	 */
	@Override
	public TaotaoResult createUser(TbUser user) {
		//有效验证
		if(StringUtils.isBlank(user.getUsername())) {
			return TaotaoResult.build(400, "用户名不能为空");
		}
		if(StringUtils.isBlank(user.getPassword())) {
			return TaotaoResult.build(400, "密码不能为空");
		}
		if(StringUtils.isBlank(user.getPhone())) {
			return TaotaoResult.build(400, "手机不能为空");
		}
		//md5加密
		user.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes()));		
		user.setCreated(new Date());
		user.setUpdated(new Date());
		//添加到数据库
		userMapper.insert(user);
		return TaotaoResult.ok();
	}

	/**
	 * 用户登录
	 */
	@Override
	public TaotaoResult userLogin(String username, String password,
			HttpServletRequest request, HttpServletResponse response) {
		
		TbUserExample example = new TbUserExample();
		Criteria criteria = example.createCriteria();
		criteria.andUsernameEqualTo(username);
		List<TbUser> list = userMapper.selectByExample(example);
		//用户名是否存在
		if(null == list || list.size()  == 0) {
			return TaotaoResult.build(500, "用户名不存在");
		}
		TbUser user = list.get(0);
		//密码是否正确
		if(!DigestUtils.md5DigestAsHex(password.getBytes()).equals(user.getPassword())) {
			return TaotaoResult.build(500, "密码错误");
		}
		//生成token
		String token = UUID.randomUUID().toString();
		//保存用户之前，把用户对象中密码清空
		user.setPassword(null);
		//用户信息写入redis，需要注入redis的实现类
		jedisClient.set(REDIS_USER_SESSION_KEY + ":" + token, JsonUtils.objectToJson(user));
		jedisClient.expire(REDIS_USER_SESSION_KEY + ":" + token, SSO_SESSION_EXPIRE);
		
		//添加写cookie的逻辑,有效期是关闭浏览器即失效，但可以设置三天不用再次登陆
		CookieUtils.setCookie(request, response, "TT_TOKEN", token);
		//此处cookieName必须为TT_TOKEN，跟js保持一致
		
		//返回token
		return TaotaoResult.ok(token);
	}

	@Override
	public TaotaoResult getUserByToken(String token) {
		// 根据token从redis中查询用户信息
		String json = jedisClient.get(REDIS_USER_SESSION_KEY + ":" + token);
		//判断是否为空
		if(StringUtils.isBlank(json)) {
			return TaotaoResult.build(400, "此session已经过期，请重新登陆");
		}
		jedisClient.expire(REDIS_USER_SESSION_KEY + ":" + token, SSO_SESSION_EXPIRE);
		
		return TaotaoResult.ok(JsonUtils.jsonToPojo(json, TbUser.class));
	}

	@Override
	public TaotaoResult userLogout(String token) {
		// 根据token从redis中查询用户信息
		String json = jedisClient.get(REDIS_USER_SESSION_KEY + ":" + token);
		//判断是否为空
		if(StringUtils.isBlank(json)) {
			return TaotaoResult.build(400, "已退出");
		}
		jedisClient.expire(REDIS_USER_SESSION_KEY + ":" + token, 0);
		return TaotaoResult.ok();
	}

}
