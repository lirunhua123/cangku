package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;

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

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种业务异常
        AddressBook addressBook= addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            //抛出业务异常
            throw new AddressBookBusinessException((MessageConstant.ADDRESS_BOOK_IS_NULL));
        }
        //查询当前用户购物车
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if(list==null || list.size()==0){
            throw new ShoppingCartBusinessException((MessageConstant.SHOPPING_CART_IS_NULL));

        }
        //向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);//1
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(orders.getNumber()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());

        orders.setUserId(userId);//通过BaseContext获得的userId
        orderMapper.insert(orders);

        List<OrderDetail> orderDetailsList = new  ArrayList<>();
        //向订单明细表插入n条数据
        for(ShoppingCart cart : list){
            OrderDetail oderDetial = new OrderDetail();
            BeanUtils.copyProperties(cart,oderDetial);
            //设置当前订单明细关联的订单id
            oderDetial.setOrderId(orders.getId());//因为xml里返回了主键值所以能直接传orders.getId()，keyProperty="id"
            orderDetailsList.add(oderDetial);

        }
        orderDetailMapper.insertBatch(orderDetailsList);
        //清空当前用户的购物车数据
        shoppingCartMapper.deleteByUseId(userId);
        //封装vo返回结果
        OrderSubmitVO orderSubmitVo = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
        return  orderSubmitVo;
    }
}
