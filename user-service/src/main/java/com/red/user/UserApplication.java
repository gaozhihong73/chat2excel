package com.red.user;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.red.user.mapper")
@ComponentScan(
        basePackages = {
                "com.red.user",   // 扫描用户模块的相关组件
                "com.red.common" // 扫描公共模块的相关组件（比如你之前写的日志切面、过滤器等）
        }
)
@Slf4j
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class);
        log.info("User-Service port:9001");
    }
}
