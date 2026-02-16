package com.example.lkpos;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 允许跨域访问的路径："/**" 代表所有后端接口
        registry.addMapping("/**")
                // 允许跨域访问的源："*" 代表允许所有前端地址（比如你的 Vite localhost:5173）
                .allowedOriginPatterns("*")
                // 允许请求的方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许的请求头
                .allowedHeaders("*")
                // 是否允许携带 cookie
                .allowCredentials(true)
                // 预检请求的有效期（秒），避免频繁发送 OPTIONS 请求
                .maxAge(3600);
    }
}