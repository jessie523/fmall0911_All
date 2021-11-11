package com.my.fmall.order.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.my.fmall.bean.OrderDetail;
import com.my.fmall.bean.OrderInfo;
import com.my.fmall.config.ActiveMQUtil;
import com.my.fmall.config.RedisUtil;
import com.my.fmall.enums.ProcessStatus;
import com.my.fmall.order.mapper.OrderDetailMapper;
import com.my.fmall.order.mapper.OrderInfoMapper;
import com.my.fmall.util.HttpClientUtil;
import com.my.fmall0911.service.OrderService;
import com.my.fmall0911.service.IPaymentService;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;
import javax.jms.Queue;
import java.util.*;

/**
 * author:zxy
 *
 * @create 2021-10-08 14:03
 */
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ActiveMQUtil activeMQUtil;

    @Reference
    private IPaymentService paymentService;

    @Transactional
    @Override
    public String saveOrder(OrderInfo orderInfo) {

        //设置创建时间
        orderInfo.setCreateTime(new Date());
        //设置过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());
        //生成第三方支付编号
        String outTradeNo = "my"+System.currentTimeMillis()+""+new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        orderInfoMapper.insertSelective(orderInfo);

        //订单详情
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();

        for (OrderDetail orderDetail : orderDetailList) {
             orderDetail.setOrderId(orderInfo.getId());
             orderDetailMapper.insertSelective(orderDetail);
        }

        //为了跳转到支付页面使用。会根据订单id进行支付
        String orderId = orderInfo.getId();

        return orderId;
    }

    /**
     * 生成流水号
     * 防止重复提交
     * @return
     */
    @Override
    public String getTradeNo(String userId) {
        Jedis jedis = redisUtil.getJedis();
        //生成key
        String tradeNoKey =  "user:"+userId+":tradeCode";
        String tradeCode = UUID.randomUUID().toString();
        jedis.setex(tradeNoKey,10*60,tradeCode);
        jedis.close();


        return tradeCode;
    }

    /**
     * 验证流水号
     * @return
     */
    @Override
    public boolean checkTradeNo(String userId,String tradeCodeNo){
        Jedis jedis = redisUtil.getJedis();
        String tradeKey = "user:"+userId+":tradeCode";
        String tradeCode = jedis.get(tradeKey);

        if(StringUtils.isNotEmpty(tradeCode) && tradeCode.equals(tradeCodeNo)){
            return true;
        }

        return false;

    }

    /**
     * 删除流水号
     * @param userId
     */
    @Override
    public void delTradeNo(String userId){
        Jedis jedis = redisUtil.getJedis();
        String tradeKey = "user:"+userId+":tradeCode";

        jedis.del(tradeKey);

        jedis.close();

    }

    @Override
    public boolean checkStock(String skuId, Integer skuNum) {

        String url = "http://www.gware.com/hasStock?skuId="+skuId+"&num="+skuNum;

        String res = HttpClientUtil.doGet(url);
        if("1".equals(res)){
            return false;
        }else{
            return false;
        }
    }

    @Override
    public OrderInfo getOrderInfo(String orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectByPrimaryKey(orderId);

        //将orderDetail放入OrderInfo
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(orderId);

        orderInfo.setOrderDetailList(orderDetailMapper.select(orderDetail));

        return orderInfo;
    }

    @Override
    public void updateOrderStatus(String orderId, ProcessStatus paid) {
        // update orderInfo set processStatus = paid , ordersStatus = paid where id = orderId;
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(paid);
        orderInfo.setOrderStatus(paid.getOrderStatus());
        orderInfoMapper.updateByPrimaryKeySelective(orderInfo);
    }

    /**
     * 减少库存
     * @param orderId
     */
    @Override
    public void sendOrderStatus(String orderId) {
        //创建消息工厂
        Connection connection = activeMQUtil.getConnection();
        String orderInfoJson = initWareOrder(orderId);
        try {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            //创建队列
            Queue queue = session.createQueue("ORDER_RESULT_QUEUE");
            //创建消息提供者
            MessageProducer producer = session.createProducer(queue);
            //创建消息对象
            ActiveMQTextMessage textMessage = new ActiveMQTextMessage();
            //orderInfo组成json字符串
            textMessage.setText(orderInfoJson);

            producer.send(textMessage);
            session.commit();

            //关闭
            connection.close();
            producer.close();
            session.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }

    }

    @Override
    public String initWareOrder(String orderId) {
        
        //根据orderId查询orderInfo
        OrderInfo orderInfo = getOrderInfo(orderId);
        //将orderInfo中有用的信息保存到map中
        Map<String,Object> map = initWareOrder(orderInfo);
        // 将map 转换为json  字符串！
        return JSON.toJSONString(map);
    }

    @Override
    public Map<String, Object> initWareOrder(OrderInfo orderInfo) {

        HashMap<String, Object> map = new HashMap<>();
        // 给map 的key 赋值！
        map.put("orderId",orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel",orderInfo.getConsigneeTel());
        map.put("orderComment",orderInfo.getOrderComment());
        map.put("orderBody","测试用例");
        map.put("deliveryAddress",orderInfo.getDeliveryAddress());
        map.put("paymentWay","2");
        map.put("wareId",orderInfo.getWareId()); // 仓库Id

        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        ArrayList<Object> arrayList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            HashMap<String, Object> map1 = new HashMap<>();

            map1.put("skuId",orderDetail.getSkuId());
            map1.put("skuNum",orderDetail.getSkuNum());
            map1.put("skuName",orderDetail.getSkuName());
            arrayList.add(map1);
        }
        map.put("details",arrayList);
        return map;
    }

    /**
     * 查询所有过期订单（当前时间> 过期时间 and 当前订单状态为未支付）
     * @return
     */
    @Override
    public List<OrderInfo> getExpiredOrderList() {

        Example example = new Example(OrderInfo.class);
        example.createCriteria().andLessThan("expireTime",new Date()).andEqualTo("processStatus",ProcessStatus.UNPAID);

        List<OrderInfo> orderInfoList = orderInfoMapper.selectByExample(example);
        return orderInfoList;
    }

    @Async//多线程实现异步并发
    @Override
    public void execExpiredOrder(OrderInfo orderInfo) {

        //将订单状态改为关闭
        updateOrderStatus(orderInfo.getId(),ProcessStatus.CLOSED);

        //关闭paymentInfo
        paymentService.closePayment(orderInfo.getId());
    }

    /**
     * 根据orderId 和 商品仓库信息 ：查询子订单
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    @Override
    public List<OrderInfo> orderSplit(String orderId, String wareSkuMap) {
        /*
        * 1 获取原始订单
        * 2 将wareSkuMap 转换成我们能操作的对象
        * 3 创建新的子订单
        * 4 给子订单赋值，并保存到数据库中
        * 5 将子订单 添加到集合中
        * 6 更新原始订单状态
        *
        * */

        List<OrderInfo> subOrderInfoList = new ArrayList<>();

        //获取原始订单
        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        //[{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);

        if(maps != null){
            //循环遍历集合
            for (Map map : maps) {
                String wareId = (String)map.get("wareId");
                //获取商品Id
                List<String> skuIds = (List<String>)map.get("skuIds");
                //创建新的子订单
                OrderInfo subOrderInfo = new OrderInfo();
                //将原始订单的数据 拷贝到新的子订单
                BeanUtils.copyProperties(orderInfoOrigin,subOrderInfo);
                subOrderInfo.setId(null);//ID必须为null
                subOrderInfo.setWareId(wareId);
                subOrderInfo.setParentOrderId(orderId);

                //价格：获取到原始订单的明细
                List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();

                // 声明一个新的子订单明细集合
                ArrayList<OrderDetail> subOrderDetailArrayList = new ArrayList<>();
                //原始的订单明细商品id
                for (OrderDetail orderDetail : orderDetailList) {
                    //仓库对应的商品Id
                    for(String skuId:skuIds){
                        if(skuId.equals(orderDetail.getSkuId())){
                             orderDetail.setOrderId(null);
                            subOrderDetailArrayList.add(orderDetail);
                        }
                    }
                }

                //将新的子订单集合 放入子订单中
                subOrderInfo.setOrderDetailList(subOrderDetailArrayList);

                //计算价格
                subOrderInfo.sumTotalAmount();
                //保存到数据库中
                saveOrder(subOrderInfo);
                // 将新的子订单添加到集合中
                subOrderInfoList.add(subOrderInfo);


            }

        }

        updateOrderStatus(orderId,ProcessStatus.SPLIT);

        return subOrderInfoList;
    }

    @Override
    public List<OrderInfo> splitOrder(String orderId,String wareSkuMap){
        List<OrderInfo> subOrderInfoList = new ArrayList<>();
        // 1 先查询原始订单
        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        // 2 wareSkuMap 反序列化
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);
        // 3 遍历拆单方案
        for (Map map : maps) {
            String wareId = (String) map.get("wareId");
            List<String> skuIds = (List<String>) map.get("skuIds");
            // 4 生成订单主表，从原始订单复制，新的订单号，父订单
            OrderInfo subOrderInfo = new OrderInfo();
            BeanUtils.copyProperties(subOrderInfo,orderInfoOrigin);
            subOrderInfo.setId(null);
            // 5 原来主订单，订单主表中的订单状态标志为拆单
            subOrderInfo.setParentOrderId(orderInfoOrigin.getId());
            subOrderInfo.setWareId(wareId);

            // 6 明细表 根据拆单方案中的skuids进行匹配，得到那个的子订单
            List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
            // 创建一个新的订单集合
            List<OrderDetail> subOrderDetailList = new ArrayList<>();
            for (OrderDetail orderDetail : orderDetailList) {
                for (String skuId : skuIds) {
                    if (skuId.equals(orderDetail.getSkuId())){
                        orderDetail.setId(null);
                        subOrderDetailList.add(orderDetail);
                    }
                }
            }
            subOrderInfo.setOrderDetailList(subOrderDetailList);
            subOrderInfo.sumTotalAmount();
            // 7 保存到数据库中
            saveOrder(subOrderInfo);
            subOrderInfoList.add(subOrderInfo);
        }
        updateOrderStatus(orderId,ProcessStatus.SPLIT);
        // 8 返回一个新生成的子订单列表
        return subOrderInfoList;
    }
}
