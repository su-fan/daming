package com.thebund1st.daming.boot.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thebund1st.daming.boot.core.SmsVerificationCodeProperties;
import com.thebund1st.daming.boot.security.SlidingWindowProperties;
import com.thebund1st.daming.core.DomainEventPublisher;
import com.thebund1st.daming.core.SmsVerification;
import com.thebund1st.daming.core.SmsVerificationRepository;
import com.thebund1st.daming.json.mixin.SmsVerificationMixin;
import com.thebund1st.daming.redis.BlockSendingRateLimitingHandler;
import com.thebund1st.daming.redis.DeleteFromRedis;
import com.thebund1st.daming.redis.DeleteFromRedisUsingRestTemplate;
import com.thebund1st.daming.redis.RedisSlidingWindowByMobileRateLimiter;
import com.thebund1st.daming.redis.RedisSmsVerificationCodeMismatchEventHandler;
import com.thebund1st.daming.redis.RedisSmsVerificationRepository;
import com.thebund1st.daming.time.Clock;
import es.moki.ratelimitj.core.limiter.request.RequestLimitRule;
import es.moki.ratelimitj.core.limiter.request.RequestRateLimiter;
import es.moki.ratelimitj.core.limiter.request.RequestRateLimiterFactory;
import es.moki.ratelimitj.redis.request.RedisRateLimiterFactory;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.resource.ClientResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Set;

// make it optional
@Slf4j
@Configuration
@Import({RedisAutoConfiguration.class, RedisRateLimiterConfiguration.class})
public class RedisConfiguration {

    @ConditionalOnMissingBean(name = "smsVerificationRedisTemplate")
    @Bean(name = "smsVerificationRedisTemplate")
    public RedisTemplate<String, SmsVerification> smsVerificationRedisTemplate(RedisConnectionFactory
                                                                                       redisConnectionFactory) {

        ObjectMapper objectMapper = buildMapper();
        RedisTemplate<String, SmsVerification> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setEnableDefaultSerializer(false);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(smsVerificationJackson2JsonRedisSerializer(objectMapper));
        return redisTemplate;
    }

    @ConditionalOnMissingBean(DeleteFromRedis.class)
    @Bean
    public DeleteFromRedis deleteFromRedis(@Qualifier("smsVerificationRedisTemplate")
                                                   RedisTemplate<String, SmsVerification> redisTemplate) {
        return new DeleteFromRedisUsingRestTemplate(redisTemplate);
    }

    @ConditionalOnMissingBean(SmsVerificationRepository.class)
    @Bean(name = "redisSmsVerificationStore")
    public RedisSmsVerificationRepository redisSmsVerificationStore(@Qualifier("smsVerificationRedisTemplate")
                                                                            RedisTemplate<String, SmsVerification> redisTemplate,
                                                                    DeleteFromRedis deleteFromRedis) {
        RedisSmsVerificationRepository bean = new RedisSmsVerificationRepository(redisTemplate, deleteFromRedis);
        return bean;
    }

    @ConditionalOnMissingBean(BlockSendingRateLimitingHandler.class)
    @Bean(name = "oneSendSmsVerificationCodeCommandInEveryXSeconds")
    public BlockSendingRateLimitingHandler redisSendSmsVerificationCodeRateLimitingHandler(
            StringRedisTemplate redisTemplate, Clock clock, SmsVerificationCodeProperties properties) {
        BlockSendingRateLimitingHandler handler =
                new BlockSendingRateLimitingHandler(redisTemplate, clock);
        handler.setExpires(properties.getBlock());
        return handler;
    }

    @Bean
    public RedisSmsVerificationCodeMismatchEventHandler redisSmsVerificationCodeMismatchEventHandler(
            StringRedisTemplate redisTemplate, DeleteFromRedis deleteFromRedis,
            DomainEventPublisher domainEventPublisher, Clock clock,
            SmsVerificationCodeProperties properties) {
        RedisSmsVerificationCodeMismatchEventHandler handler =
                new RedisSmsVerificationCodeMismatchEventHandler(redisTemplate, deleteFromRedis, domainEventPublisher, clock);
        handler.setThreshold(properties.getMaxFailures());
        return handler;
    }

    private Jackson2JsonRedisSerializer<SmsVerification> smsVerificationJackson2JsonRedisSerializer(ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<SmsVerification> smsVerificationJackson2JsonRedisSerializer =
                new Jackson2JsonRedisSerializer<>(SmsVerification.class);
        smsVerificationJackson2JsonRedisSerializer.setObjectMapper(objectMapper);
        return smsVerificationJackson2JsonRedisSerializer;
    }

    //TODO make it reusable for other components
    public static ObjectMapper buildMapper() {
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
