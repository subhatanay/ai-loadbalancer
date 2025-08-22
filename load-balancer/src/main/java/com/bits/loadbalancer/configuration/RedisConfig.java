package com.bits.loadbalancer.configuration;

import java.time.Duration;

import com.bits.loadbalancer.services.ServiceDiscoveryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

@EnableCaching
@Configuration
public class RedisConfig {

    @Value("${loadbalancer.metrics.cache-ttl:30s}")
    private Duration cacheTtl;

    @Bean
    public RestTemplate restTemplate() {
        // Use simple HTTP client factory with increased timeouts to prevent EOF errors
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 second connection timeout
        factory.setReadTimeout(60000);    // 60 second read timeout
        
        // Create RestTemplate with simple factory
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // Configure URI builder factory with proper encoding to prevent HTTP corruption
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);
        restTemplate.setUriTemplateHandler(uriBuilderFactory);
        
        // Add interceptor to prevent response corruption and handle connection issues
        restTemplate.getInterceptors().add((request, body, execution) -> {
            // Ensure proper content type and encoding headers
            request.getHeaders().set("Accept", "application/json");
            request.getHeaders().set("Accept-Charset", "UTF-8");
            // Disable compression to prevent binary corruption
            request.getHeaders().set("Accept-Encoding", "identity");
            // Add connection keep-alive to prevent premature closure
            request.getHeaders().set("Connection", "keep-alive");
            return execution.execute(request, body);
        });
        
        return restTemplate;
    }
    
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // Use GenericJackson2JsonRedisSerializer for compatibility with services
        org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer genericSerializer = 
            new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer();
            
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheTtl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(genericSerializer));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfiguration)
                .transactionAware()
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Key serialization
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value serialization - Use GenericJackson2JsonRedisSerializer for compatibility
        org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer genericSerializer = 
            new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(genericSerializer);
        template.setHashValueSerializer(genericSerializer);

        template.setDefaultSerializer(genericSerializer);
        template.afterPropertiesSet();

        return template;
    }

    @Bean
    public ServiceDiscoveryClient serviceDiscoveryClient(org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate) {
        return new ServiceDiscoveryClient(stringRedisTemplate);
    }
}
