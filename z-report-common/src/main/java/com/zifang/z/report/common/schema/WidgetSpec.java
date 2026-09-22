package com.zifang.z.report.common.schema;

import java.util.List;

/**
 * 图表组件规格: 绑定数据集 + 视觉编码 + 过滤。
 * 渲染链路: WidgetSpec + DatasetSchema → 引擎取数/聚合 → echarts option。
 */
public class WidgetSpec {

    private String id;
    private ChartType type;
    private String title;
    private String datasetId;
    private WidgetEncode encode;
    /** 度量聚合方式, 默认 SUM */
    private AggType agg;
    private List<WidgetFilter> filters;

    public WidgetSpec() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ChartType getType() {
        return type;
    }

    public void setType(ChartType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public WidgetEncode getEncode() {
        return encode;
    }

    public void setEncode(WidgetEncode encode) {
        this.encode = encode;
    }

    public AggType getAgg() {
        return agg;
    }

    public void setAgg(AggType agg) {
        this.agg = agg;
    }

    public List<WidgetFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<WidgetFilter> filters) {
        this.filters = filters;
    }
}
