package com.campus.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * MVC 配置：把对账账单目录以只读静态资源暴露，便于财务下载原始账单。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = new File(appProperties.getUploadDir()).getAbsolutePath().replace("\\", "/");
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + location + "/");
    }
}
