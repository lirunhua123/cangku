package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.jwt")
/**sky:
 jwt:
 # 设置jwt签名加密时使用的秘钥
 admin-secret-key: itcast
 # 设置jwt过期时间
 admin-ttl: 7200000
 # 设置前端传递过来的令牌名称
 admin-token-name: token

 **/
 @Data
public class JwtProperties {

    /**
     * 管理端员工生成jwt令牌相关配置
     */
    private String adminSecretKey;
    private long adminTtl;
    private String adminTokenName;

    /**
     * 用户端微信用户生成jwt令牌相关配置
     */
    private String userSecretKey;
    private long userTtl;
    private String userTokenName;

}
