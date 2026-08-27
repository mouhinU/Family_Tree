package com.mouhin.family.tree.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 族谱管理系统启动类
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@SpringBootApplication(scanBasePackages = "com.mouhin.family.tree")
@MapperScan("com.mouhin.family.tree.infrastructure.persistence.mapper")
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
