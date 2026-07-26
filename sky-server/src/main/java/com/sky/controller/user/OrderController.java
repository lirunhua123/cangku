package com.sky.controller.user;

import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "C端-订单接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 历史订单分页查询
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单分页查询")
    public Result<PageResult> historyOrders(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("查询历史订单：page={}, pageSize={}", page, pageSize);
        PageResult pageResult = orderService.pageQuery4User(page, pageSize);
        return Result.success(pageResult);
    }
}
