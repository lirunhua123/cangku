package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 分页查询当前用户的订单（按时间倒序）
     */
    @Select("select * from orders where user_id = #{userId} order by order_time desc")
    List<Orders> pageQueryByUserId(@Param("userId") Long userId);

    /**
     * 根据订单id查询订单详情
     */
    @Select("select * from order_detail where order_id = #{orderId}")
    List<com.sky.entity.OrderDetail> getOrderDetailsByOrderId(@Param("orderId") Long orderId);
}
