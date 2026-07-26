package com.sky.controller.notify;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.WeChatProperties;
import com.sky.service.OrderService;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

/**
 * 支付回调相关接口
 */
@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private WeChatProperties weChatProperties;

    /**
     * 支付成功回调
     * @param request
     */
    @RequestMapping("/paySuccess")
    public void paySuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 1. 读取请求数据
        String body = readData(request);
        log.info("支付成功回调：{}", body);

        // 2. 数据解密
        String plainText = decryptData(body);
        log.info("解密后的文本：{}", plainText);

        // 3. 解析明文
        JSONObject jsonObject = JSON.parseObject(plainText);
        String outTradeNo = jsonObject.getString("out_trade_no"); // 商户订单号
        String transactionId = jsonObject.getString("transaction_id"); // 微信支付系统生成的订单号
        log.info("商户订单号：{}", transactionId);
        log.info("微信支付交易号：{}", transactionId);

        // 4. 修改订单状态
        orderService.paySuccess(outTradeNo);

        // 5. 给微信响应
        responseToWeixin(response);
    }

    /**
     * 读取请求数据
     */
    private String readData(HttpServletRequest request) throws Exception {
        BufferedReader reader = request.getReader();
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(line);
        }
        return result.toString();
    }

    /**
     * 数据解密
     */
    private String decryptData(String body) throws Exception {
        JSONObject resultObject = JSON.parseObject(body);
        JSONObject resource = resultObject.getJSONObject("resource");
        String ciphertext = resource.getString("ciphertext");
        String nonce = resource.getString("nonce");
        String associatedData = resource.getString("associated_data");

        AesUtil aesUtil = new AesUtil(weChatProperties.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        return aesUtil.decryptToString(
                associatedData.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8),
                ciphertext
        );
    }

    /**
     * 给微信返回确认
     */
    private void responseToWeixin(HttpServletResponse response) throws Exception {
        response.setStatus(200);
        response.addHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
        response.getWriter().write("{\"code\":\"SUCCESS\",\"message\":\"SUCCESS\"}");
        response.getWriter().flush();
    }
}
