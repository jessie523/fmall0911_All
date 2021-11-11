package com.my.fmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.my.fmall.bean.*;
import com.my.fmall.config.LoginRequie;
import com.my.fmall0911.service.CartService;
import com.my.fmall0911.service.ManageService;
import com.my.fmall0911.service.OrderService;
import com.my.fmall0911.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * author:zxy
 *
 * @create 2021-09-13 11:48
 */
@Controller
@RequestMapping("/order")
public class OrderController {

//    @Autowired
    @Reference
    private UserService userService;


    @Reference
    private CartService cartService;

    @Reference
    private OrderService orderService;

    @Reference
    private ManageService manageService;


    @GetMapping("/trace")
    public String trace(){
        return "index";
    }

    @ResponseBody
    @GetMapping("/addrList/{userId}")
    public List<UserAddress> getUserAddress(@PathVariable("userId")String userId){
        return userService.getUserAddressList(userId);
    }

    //    @RequestMapping("trade")
    //    public String trade(){
    //        // 返回一个视图名称叫index.html
    //        return "index";
    //    }
    // http://localhost:8081?userId=1
    @RequestMapping("trade")
//    @ResponseBody // 第一个返回json 字符串，fastJson.jar 第二直接将数据显示到页面！
    @LoginRequie
    public String trade(HttpServletRequest request){
        String userId = (String) request.getAttribute("userId");
        // 返回一个视图名称叫index.html
        // return userService.getUserAddressList(userId);
        List<UserAddress> userAddressList = userService.getUserAddressList(userId);

        request.setAttribute("userAddressList",userAddressList);

        // 展示送货清单：
        // 数据来源：勾选的购物车！user:userId:checked！
        List<CartInfo> cartInfoList = cartService.getCartCheckedList(userId);

        // 声明一个集合来存储订单明细
        ArrayList<OrderDetail> orderDetailArrayList = new ArrayList<>();
        // 将集合数据赋值OrderDetail
        for (CartInfo cartInfo : cartInfoList) {
            OrderDetail orderDetail = new OrderDetail();

            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getCartPrice());

            orderDetailArrayList.add(orderDetail);
        }

        // 总金额：
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailArrayList);
        // 调用计算总金额的方法  {totalAmount}
        orderInfo.sumTotalAmount();

        request.setAttribute("totalAmount",orderInfo.getTotalAmount());
        // 保存送货清单集合
        request.setAttribute("orderDetailArrayList",orderDetailArrayList);

        String tradeNo = orderService.getTradeNo(userId);
        request.setAttribute("tradeNo",tradeNo);
        return "trade";
    }

    @RequestMapping("submitOrder")
    @LoginRequie
    public String submitOrder(HttpServletRequest request,OrderInfo orderInfo){
        String userId = (String) request.getAttribute("userId");
        // orderInfo 中还缺少一个userId
        orderInfo.setUserId(userId);

        // 判断是否是重复提交
        // 先获取页面的流水号
        String tradeNo = request.getParameter("tradeNo");
        // 调用比较方法
        boolean result = orderService.checkTradeNo(userId, tradeNo);
        // 是重复提交
        if (!result){
            request.setAttribute("errMsg","订单已提交，不能重复提交！");
            return "tradeFail";
        }

        // 验证库存！ /hasStock?skuId=10221&num=2
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            boolean flag = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
            if (!flag){
                request.setAttribute("errMsg",orderDetail.getSkuName()+"商品库存不足！");
                return "tradeFail";
            }
            // 获取skuInfo 对象b
            SkuInfo skuInfo =	manageService.getSkuInfo(orderDetail.getSkuId());
            //
            int res = skuInfo.getPrice().compareTo(orderDetail.getOrderPrice());
            if (res!=0){
                request.setAttribute("errMsg",orderDetail.getSkuName()+"价格不匹配");
                cartService.loadCartCache(userId);
                return "tradeFail";
            }

            // 重新查询实时价格并修改缓存
            //            BigDecimal res = skuInfo.getPrice().subtract(orderDetail.getOrderPrice());
            //            if (res.equals(0)){
            //
            //            }

        }

        // 调用服务层
        String orderId = orderService.saveOrder(orderInfo);

        // 删除流水号
        orderService.delTradeNo(userId);

        // 支付
        return "redirect://payment.gmall.com/index?orderId="+orderId;
    }

    @RequestMapping("orderSplit")
    @ResponseBody
    public String orderSplit(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        String wareSkuMap = request.getParameter("wareSkuMap");
        // 定义订单集合
        List<OrderInfo> subOrderInfoList = orderService.splitOrder(orderId,wareSkuMap);
        List<Map> wareMapList=new ArrayList<>();
        for (OrderInfo orderInfo : subOrderInfoList) {
            Map map = orderService.initWareOrder(orderInfo);
            wareMapList.add(map);
        }
        return JSON.toJSONString(wareMapList);
    }
}
