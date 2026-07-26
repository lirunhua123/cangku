package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {
    //微信服务接口地址
    public static final String wx_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatProperties weChatProperties;

    /**
     * 微信登录
     * @param userloginDTO
     * @return
     */
    @Override
    public User wexinlogin(UserLoginDTO userloginDTO) {
        Map<String,String> map = new HashMap<>();
        map.put("appid",weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("js_code", userloginDTO.getCode());
        map.put("grant_type","authorization_code");
        String json = HttpClientUtil.doGet(wx_LOGIN,map);
        System.err.println("微信API返回内容: " + json);
        JSONObject jsonObject = JSONObject.parseObject(json);
        String openid = jsonObject.getString("openid");

        //判断是否为空
        if(openid==null){
            String errcode = jsonObject.getString("errcode");
            String errmsg = jsonObject.getString("errmsg");
            System.err.println("微信登录失败: errcode=" + errcode + ", errmsg=" + errmsg);
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        //判断是否为新用户
        User user = userMapper.getByOpenid(openid);
        if(user == null){
            user= User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }


        return user;
    }
}
