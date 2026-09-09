package com.red.file;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 文件服务主启动类
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.red.file.mapper")
@ComponentScan(basePackages = {"com.red.common", "com.red.file"})
public class FileApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
        log.info("File Application Start Success Port: 9003");
    }
}
