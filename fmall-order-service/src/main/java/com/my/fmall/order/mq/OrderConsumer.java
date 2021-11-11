package com.my.fmall.order.mq;

import com.my.fmall.enums.ProcessStatus;
import com.my.fmall0911.service.OrderService;
import com.sun.media.jfxmedia.logging.Logger;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;

/**
 * author:zxy
 *
 * @create 2021-10-12 15:38
 */
@Component
public class OrderConsumer {

    @Autowired
    private OrderService orderService;

    /**
     * destination:消息队列的名字
     * containerFactory:使用哪个消息监听器工厂
     * @param activeMQMapMessage
     * @throws JMSException
     */
    @JmsListener(destination = "PAYMENT_RESULT_QUEUE",containerFactory = "jmsQueueListener")
    public void consumerPaymentResult(ActiveMQMapMessage activeMQMapMessage) throws JMSException {
        String orderId = activeMQMapMessage.getString("orderId");
        String result = activeMQMapMessage.getString("result");


        System.out.println("orderId:"+orderId);
        System.out.println("result:"+result);

        if("success".equalsIgnoreCase(result)){
//            更新支付状态
            orderService.updateOrderStatus(orderId, ProcessStatus.PAID);
//            通知减库存
            orderService.sendOrderStatus(orderId);
            orderService.updateOrderStatus(orderId, ProcessStatus.NOTIFIED_WARE);
        }else{
            orderService.updateOrderStatus(orderId, ProcessStatus.UNPAID);
        }
    }

    /**
     * 库存系统发送消息队列 通知 订单系统 减库存
     * @param mapMessage
     * @throws JMSException
     */
    @JmsListener(destination = "SKU_DEDUCT_QUEUE",containerFactory = "jmsQueueListener")
    public void consumeSkuDeduct(MapMessage mapMessage) throws JMSException {
        // 通过mapMessage获取
        String orderId = mapMessage.getString("orderId");
        String status = mapMessage.getString("status");
        if ("DEDUCTED".equals(status)){
            orderService.updateOrderStatus(orderId, ProcessStatus.DELEVERED);
        }
    }
}
