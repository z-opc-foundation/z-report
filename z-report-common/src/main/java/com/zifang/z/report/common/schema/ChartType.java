package com.zifang.z.report.common.schema;

/**
 * 图表类型 (M1 五件套)。
 * TABLE/KPI 非原生 echarts 类型, 由前端通用渲染器按约定结构解释。
 */
public enum ChartType {
    LINE,
    BAR,
    PIE,
    TABLE,
    KPI
}
