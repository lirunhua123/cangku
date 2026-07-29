package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;

    // 店铺地址
    @Value("${sky.shop.address}")
    private String shopAddress;

    // 百度地图AK
    @Value("${sky.baidu.ak}")
    private String ak;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 处理各种业务异常
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 检查用户的收货地址是否超出配送范围
        checkOutOfRange(addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        // 查询当前用户购物车
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list == null || list.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(addressBook.getDetail());
        orderMapper.insert(orders);

        // 向订单明细表插入n条数据
        List<OrderDetail> orderDetailsList = new ArrayList<>();
        for (ShoppingCart cart : list) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailsList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailsList);

        // 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUseId(userId);

        // 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
        return orderSubmitVO;
    }

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getByOpenid("");

        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(),
                new BigDecimal(0.01),
                "苍穹外卖订单",
                user.getOpenid()
        );

        if (jsonObject != null) {
            return new OrderPaymentVO(
                    jsonObject.getString("timeStamp"),
                    jsonObject.getString("nonceStr"),
                    jsonObject.getString("package"),
                    jsonObject.getString("signType"),
                    jsonObject.getString("paySign")
            );
        } else {
            throw new OrderBusinessException("微信支付失败，没有响应");
        }
    }

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {
        // 根据订单号查订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 修改订单状态
        ordersDB.setPayStatus(Orders.PAID);
        ordersDB.setStatus(Orders.TO_BE_CONFIRMED);
        ordersDB.setCheckoutTime(LocalDateTime.now());

        orderMapper.update(ordersDB);
        //通过webSocket向用户客户端浏览器推送消息 type orderId content
        Map map = new HashMap();
        map.put("type",1);//1表示来单提醒，2表示客户催单
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号："+outTradeNo);

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 用户端历史订单分页查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery4User(int page, int pageSize, Integer status) {
        Long userId = BaseContext.getCurrentId();

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(userId);
        ordersPageQueryDTO.setStatus(status);

        PageHelper.startPage(page, pageSize);
        List<Orders> ordersList = orderMapper.pageQuery(ordersPageQueryDTO);
        Page<Orders> pageResult = (Page<Orders>) ordersList;

        List<OrderVO> orderVOList = new ArrayList<>();
        if (pageResult != null && pageResult.getTotal() > 0) {
            for (Orders orders : pageResult.getResult()) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                // 查询订单菜品信息
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orders.getId());
                orderVO.setOrderDetailList(orderDetails);

                // 拼接订单菜品信息字符串
                String orderDishes = orderDetails.stream()
                        .map(detail -> detail.getName() + "×" + detail.getNumber())
                        .collect(Collectors.joining(";"));
                orderVO.setOrderDishes(orderDishes);

                orderVOList.add(orderVO);
            }
        }

        return new PageResult(pageResult.getTotal(), orderVOList);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    public OrderVO details(Long id) {
        Orders orders = orderMapper.getById(id);

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orders.getId());

        // 封装成OrderVO
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetails);

        return orderVO;
    }

    /**
     * 用户取消订单
     * @param id
     */
    public void userCancelById(Long id) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 只有待付款的订单可以取消
        if (!ordersDB.getStatus().equals(Orders.PENDING_PAYMENT)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 如果已支付则退款
        if (ordersDB.getPayStatus().equals(Orders.PAID)) {
            weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount()
            );
            ordersDB.setPayStatus(Orders.REFUND);
        }

        // 修改订单状态为取消
        ordersDB.setStatus(Orders.CANCELLED);
        ordersDB.setCancelTime(LocalDateTime.now());
        ordersDB.setCancelReason("用户取消");
        orderMapper.update(ordersDB);
    }

    /**
     * 再来一单
     * @param id
     */
    @Transactional
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();

        // 查询订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单明细转为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(orderDetail -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart);
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());

        // 批量插入购物车
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 条件搜索订单（商家端）
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        List<Orders> ordersList = orderMapper.pageQuery(ordersPageQueryDTO);
        Page<Orders> pageResult = (Page<Orders>) ordersList;

        List<OrderVO> orderVOList = new ArrayList<>();
        if (pageResult != null && pageResult.getTotal() > 0) {
            for (Orders orders : pageResult.getResult()) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                // 拼接订单菜品信息
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orders.getId());
                String orderDishes = orderDetails.stream()
                        .map(detail -> detail.getName() + "×" + detail.getNumber())
                        .collect(Collectors.joining(";"));
                orderVO.setOrderDishes(orderDishes);

                orderVOList.add(orderVO);
            }
        }

        return new PageResult(pageResult.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    public OrderStatisticsVO statistics() {
        // 待接单
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        // 已接单（派送中）
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        // 派送中
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将数据封装到OrderStatisticsVO
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed == null ? 0 : toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed == null ? 0 : confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress == null ? 0 : deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        // 只有待接单的订单可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 如果已付款，需要退款
        if (ordersDB.getPayStatus().equals(Orders.PAID)) {
            weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount()
            );
            // 更新支付状态为退款
            ordersDB.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态为取消，记录拒绝原因和时间
        ordersDB.setStatus(Orders.CANCELLED);
        ordersDB.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        ordersDB.setCancelTime(LocalDateTime.now());

        orderMapper.update(ordersDB);
    }

    /**
     * 商家取消订单
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 如果已付款，需要退款
        if (ordersDB.getPayStatus().equals(Orders.PAID)) {
            weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount()
            );
            ordersDB.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态为取消
        ordersDB.setStatus(Orders.CANCELLED);
        ordersDB.setCancelReason(ordersCancelDTO.getCancelReason());
        ordersDB.setCancelTime(LocalDateTime.now());

        orderMapper.update(ordersDB);
    }

    /**
     * 派送订单
     * @param id
     */
    public void delivery(Long id) {
        Orders ordersDB = orderMapper.getById(id);

        // 只有已接单的订单可以派送
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 完成订单
     * @param id
     */
    public void complete(Long id) {
        Orders ordersDB = orderMapper.getById(id);

        // 只有派送中的订单可以完成
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    @Override
    public void reminder(Long id) {
        Orders ordersDB = orderMapper.getById(id);

        // 只有派送中的订单可以完成
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map map = new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        map.put("content","订单号："+ordersDB.getNumber());
        //通过websocket向客户端浏览器推送消息
        //json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(JSON.toJSONString(map));

    }

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map<String, String> map = new HashMap<>();
        map.put("address", shopAddress);
        map.put("output", "json");
        map.put("ak", ak);

        // 获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if (jsonObject.getInteger("status") != 0) {
            throw new OrderBusinessException("店铺地址解析失败");
        }

        JSONObject shopLocation = jsonObject.getJSONObject("result").getJSONObject("location");
        String shopLng = shopLocation.getString("lng");
        String shopLat = shopLocation.getString("lat");

        // 获取用户收货地址的经纬度坐标
        map.put("address", address);
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if (jsonObject.getInteger("status") != 0) {
            throw new OrderBusinessException("收货地址解析失败");
        }

        JSONObject userLocation = jsonObject.getJSONObject("result").getJSONObject("location");
        String userLng = userLocation.getString("lng");
        String userLat = userLocation.getString("lat");

        // 计算两点之间的距离（米）
        if (!checkDistance(shopLng, shopLat, userLng, userLat)) {
            throw new OrderBusinessException("超出配送范围");
        }
    }

    /**
     * 计算两点之间距离，判断是否在配送范围内（5公里）
     */
    private boolean checkDistance(String shopLng, String shopLat, String userLng, String userLat) {
        double lat1 = Double.parseDouble(shopLat);
        double lng1 = Double.parseDouble(shopLng);
        double lat2 = Double.parseDouble(userLat);
        double lng2 = Double.parseDouble(userLng);

        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);

        double s = 2 * Math.asin(Math.sqrt(
                Math.pow(Math.sin(a / 2), 2)
                        + Math.cos(radLat1) * Math.cos(radLat2)
                        * Math.pow(Math.sin(b / 2), 2)));
        s = s * 6371.393; // 地球半径，km
        s = Math.round(s * 1000);

        // 判断距离是否在5公里（5000米）以内
        return s <= 5000;
    }
}
