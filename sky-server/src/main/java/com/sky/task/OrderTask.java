package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */
@Slf4j
@Component
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;
    /**
     * 处理超时订单方法
     */
    @Scheduled(cron = "0 * * * * ?")//每分钟触发一次
    public void processTimeoutOrder(){
        log.info("定时处理超时订单：{}", LocalDateTime.now());
        //当前时间减15分钟
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

        List<Orders> list =  orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT,time);
        if(list.size()>0&&list!=null){
            for(Orders orders:list){
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 处理一直处于派送中状态得订单
     */
    @Scheduled(cron = "0 0 1 * * ?")//每天凌晨一点触发一次
    public void processDeliveryOrder(){
        log.info("定时处理派送中的订单：{}", LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);

        List<Orders> list = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS,time);
        if(list.size()>0&&list!=null){
            for(Orders orders:list){
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }
}
