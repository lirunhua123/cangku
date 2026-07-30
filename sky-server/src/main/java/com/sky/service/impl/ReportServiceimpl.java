package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ReportServiceimpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService  workspaceService;

    /**
     * 统计指定时间区间内的营业额数据
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList();

        dateList.add(begin);
        while(!begin.equals(end)){
            //日期计算，计算指定日期的最后一天对应的日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //StringUtils.join(dateList,",");
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            //查询date日期对应的营业额数据，状态为已完成的订单的总金额
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);//min最小时刻，就是一天开始的时候
            LocalDateTime endTIme = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTIme);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover ==null?0.0:turnover;
            turnoverList.add(turnover);

        }

        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList.toArray(), ","))
                .turnoverList(StringUtils.join(turnoverList.toArray(), ","))
                .build();

    }

    /**
     * 统计指定时间区间内的营业额数据
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天新增用户
        List<Integer> newUserList = new ArrayList<>();
        //存放每天总用户
        List<Integer> totalUserList = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTIme = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();

            map.put("end",endTIme);
            //总数量
            Integer totalUser = userMapper.countByMap(map);
            //新用户
            map.put("begin",beginTime);
            Integer newUser = userMapper.countByMap(map);

            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }


        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList.toArray(), ","))
                .totalUserList(StringUtils.join(totalUserList.toArray(), ","))
                .newUserList(StringUtils.join(newUserList.toArray(), ","))
                .build();
    }

    @Override
    public OrderReportVO getOrderStatics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        List<Integer> ordercountList = new ArrayList<>();
        List<Integer> validordercountList = new ArrayList<>();

        //遍历，查询每天订单总数和有效订单数
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTIme = LocalDateTime.of(date, LocalTime.MAX);

            //订单总数，status 传 null 表示不限状态
            Integer ordercount = getOrderCount(beginTime, endTIme, null);
            //有效订单数，status 传已完成
            Integer validOrderCount = getOrderCount(beginTime, endTIme, Orders.COMPLETED);



            ordercountList.add(ordercount);
            validordercountList.add(validOrderCount);
        }

        //时间区间内订单总数量
        int totalOrderCount = ordercountList.stream().mapToInt(Integer::intValue).sum();

        //时间区间内有效订单总数量
        int validOrderCount = validordercountList.stream().mapToInt(Integer::intValue).sum();

        //订单完成率
        double orderCompletionRate = totalOrderCount == 0 ? 0.0 : (double) validOrderCount / totalOrderCount;


        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList.toArray(), ","))
                .orderCountList(StringUtils.join(ordercountList.toArray(), ","))
                .validOrderCountList(StringUtils.join(validordercountList.toArray(), ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 销量排名
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTIme = LocalDateTime.of(begin, LocalTime.MAX);
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTIme);
        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");

        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");
        return SalesTop10ReportVO.builder().nameList(nameList).numberList(numberList).build();
    }

    /**
     * 导出运营数据报表
     * @param response
     */
    public void expotBusinessData(HttpServletResponse response) {
        //1查询数据库 近30天的
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        LocalDate dateEnd = LocalDate.now().minusDays(1);

        LocalDateTime begin = LocalDateTime.of(dateBegin, LocalTime.MIN);//那一天的0.0分0秒
        LocalDateTime end = LocalDateTime.of(dateEnd, LocalTime.MAX);//那一天的最后一秒
        //查询概览数据

        BusinessDataVO bussinessDataVO = workspaceService.getBusinessData(begin, end);

        //2通过poi写入excel文件中

        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");


        try {
            //基于模板文件创建一个新的excel文件
            XSSFWorkbook excel = new XSSFWorkbook(in);

            //获取表格文件的Sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");
            //填充数据--时间
            sheet.getRow(1).getCell(1).setCellValue("时间："+begin+"至"+end);
            //获得第四行
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(bussinessDataVO.getTurnover());

            row.getCell(4).setCellValue(bussinessDataVO.getOrderCompletionRate());

            row.getCell(6).setCellValue(bussinessDataVO.getNewUsers());

            row = sheet.getRow(4);
            row.getCell(2).setCellValue(bussinessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(bussinessDataVO.getUnitPrice());
            //填充明细数据
            for(int i = 0;i<30;i++){
                LocalDate date = dateBegin.plusDays(i);
                //查询某一天的数据 营业
                BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(date,LocalTime.MIN),LocalDateTime.of(date,LocalTime.MAX));;
                //获得某一行
                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }

            //3.通过输出流将excel文件下载到客户端浏览器
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);

            //关闭资源
            out.close();
            excel.close();

        }catch (Exception e){
            e.printStackTrace();
        }



    }

    /**
     * 根据时间区间和状态统计订单数量
     * @param begin
     * @param end
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end, Integer status) {
        Map map = new HashMap();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status", status);

        Integer count = orderMapper.countByMap(map);
        return count == null ? 0 : count;
    }
}
