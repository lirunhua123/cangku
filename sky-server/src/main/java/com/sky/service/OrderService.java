package com.sky.service;

import com.sky.result.PageResult;

public interface OrderService {

    /**
     * 用户端历史订单分页查询
     * @param page 页码
     * @param pageSize 每页条数
     * @return
     */
    PageResult pageQuery4User(int page, int pageSize);
}
