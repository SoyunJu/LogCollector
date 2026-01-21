package com.soyunju.logcollector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public RedisTemplate<String, ErrorLogRequest> errorLogRequestRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper
    ) {
        RedisTemplate<String, ErrorLogRequest> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        var serializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

}

