package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.context.BaseContext;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 用户端历史订单分页查询
     */
    @Override
    public PageResult pageQuery4User(int page, int pageSize) {
        Long userId = BaseContext.getCurrentId();

        PageHelper.startPage(page, pageSize);
        List<Orders> ordersList = orderMapper.pageQueryByUserId(userId);
        Page<Orders> pageResult = (Page<Orders>) ordersList;

        List<OrderVO> orderVOList = new ArrayList<>();
        for (Orders orders : pageResult.getResult()) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);

            List<OrderDetail> orderDetails = orderMapper.getOrderDetailsByOrderId(orders.getId());
            orderVO.setOrderDetailList(orderDetails);

            // 拼接订单菜品信息
            StringBuilder sb = new StringBuilder();
            if (orderDetails != null && !orderDetails.isEmpty()) {
                for (OrderDetail detail : orderDetails) {
                    sb.append(detail.getName()).append("×").append(detail.getNumber()).append(";");
                }
                sb.deleteCharAt(sb.length() - 1);
            }
            orderVO.setOrderDishes(sb.toString());

            orderVOList.add(orderVO);
        }

        return new PageResult(pageResult.getTotal(), orderVOList);
    }
}
