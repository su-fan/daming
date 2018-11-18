package com.thebund1st.daming.boot.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thebund1st.daming.core.SmsVerification;
import com.thebund1st.daming.json.mixin.SmsVerificationMixin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// make it optional
@Slf4j
@Configuration
public class RedisConfiguration {

    @Bean
    public RedisTemplate<String, SmsVerification> smsVerificationRedisTemplate(RedisConnectionFactory
                                                                                       redisConnectionFactory) {

        ObjectMapper objectMapper = buildMapper();
        RedisTemplate<String, SmsVerification> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(smsVerificationJackson2JsonRedisSerializer(objectMapper));
        return redisTemplate;
    }

    private Jackson2JsonRedisSerializer<SmsVerification> smsVerificationJackson2JsonRedisSerializer(ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<SmsVerification> smsVerificationJackson2JsonRedisSerializer =
                new Jackson2JsonRedisSerializer<>(SmsVerification.class);
        smsVerificationJackson2JsonRedisSerializer.setObjectMapper(objectMapper);
        return smsVerificationJackson2JsonRedisSerializer;
    }

    //TODO make it reusable for other components
    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(mapper.getSerializationConfig()
                .getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        mapper.addMixIn(SmsVerification.class, SmsVerificationMixin.class);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}