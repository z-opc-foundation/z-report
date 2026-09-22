package com.zifang.z.report.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 独立部署启动器。starter 已自动装配全部能力, 此处零额外配置。
 */
@SpringBootApplication(
        // z-report 的 JDBC 源为自管理 Druid (z-boot 约定), 排除容器级数据源自动配置
        exclude = {org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure.class})
public class ZReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZReportApplication.class, args);
    }
}
