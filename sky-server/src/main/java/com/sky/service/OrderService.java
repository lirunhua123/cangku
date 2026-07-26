package com.sky.service;

import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderSubmitVO;

public interface OrderService {

    /**
     * 用户端历史订单分页查询
     * @param page 页码
     * @param pageSize 每页条数
     * @return
     */
    PageResult pageQuery4User(int page, int pageSize);

    /**
     * 用户 下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);
}
