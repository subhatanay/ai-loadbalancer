package com.bits.loadbalancer.configuration;

import com.bits.loadbalancer.configuration.TraceInterceptor;
import com.bits.loadbalancer.services.ExperienceLoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private ExperienceLoggingInterceptor interceptor;

    @Autowired
    private TraceInterceptor traceInterceptor;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/proxy/**");
        registry.addInterceptor(traceInterceptor)
                .addPathPatterns("/proxy/**");
    }
}


