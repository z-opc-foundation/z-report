package com.zifang.z.report.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * z-report 自动装配: 扫描 com.zifang.z.report 下全部能力
 * (数据源/数据集/渲染/Book/AI/web)。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.zifang.z.report")
public class ZReportAutoConfiguration {
}
