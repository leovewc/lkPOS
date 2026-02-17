package com.example.lkpos;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 获取项目运行时的当前目录
        String currentPath = System.getProperty("user.dir");
        // 如果没有 uploads 文件夹，自动创建一个
        File uploadDir = new File(currentPath + "/uploads");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 🌟 核心：将前端 /uploads/** 的请求，映射到你电脑硬盘的 uploads 文件夹
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + currentPath + "/uploads/");
    }
}