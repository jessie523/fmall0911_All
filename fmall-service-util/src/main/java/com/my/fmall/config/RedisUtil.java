package com.my.fmall.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * author:zxy
 *
 * @create 2021-09-22 10:42
 */

public class RedisUtil {

    private JedisPool jedisPool;
    @Bean
    public void initJedisPool(String host,int port,int database){
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
//        总数
        jedisPoolConfig.setMaxTotal(200);
//        获得连接时等待的最大毫秒
        jedisPoolConfig.setMaxWaitMillis(10*1000);
//        最少剩余数
        jedisPoolConfig.setMinIdle(10);
//        如果到最大数，设置等待
        jedisPoolConfig.setBlockWhenExhausted(true);
//        在获取连接时，检查是否有效
        jedisPoolConfig.setTestOnBorrow(true);
//        创建连接池
        jedisPool = new JedisPool(jedisPoolConfig,host,port,20*1000);

    }


    public Jedis getJedis(){
        Jedis jedis = jedisPool.getResource();
        return jedis;
    }
}
