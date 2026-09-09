package com.red.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 */
@Configuration
public class RedisConfig {


    /**
     * 构建 ReactiveRedisTemplate
     * “我要在响应式环境下使用 Redis，并且我不希望在 Redis 库里看到乱码。请把所有的键和值都当作纯文本字符串（UTF-8）来处理。”
     * @param connectionFactory 连接工厂
     * @return ReactiveRedisTemplate示例对象
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        // 1. 序列化
        StringRedisSerializer serializer = new StringRedisSerializer();

        // 2. 创建builder
        RedisSerializationContext.RedisSerializationContextBuilder<String, String> builder = RedisSerializationContext.newSerializationContext();


        RedisSerializationContext<String, String> context = builder
                .key(serializer)      // 设置 Key 的序列化器
                .value(serializer)    // 设置 Value 的序列化器
                .hashKey(serializer)  // 设置 Hash 结构中 Key 的序列化器
                .hashValue(serializer)// 设置 Hash 结构中 Value 的序列化器
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
