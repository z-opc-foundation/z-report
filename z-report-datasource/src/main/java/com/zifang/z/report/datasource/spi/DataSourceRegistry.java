package com.zifang.z.report.datasource.spi;

import com.zifang.z.report.common.schema.DataSourceDef;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源注册中心: id → 实例 (M1 内存态, M2 落库 + 凭证加密)。
 */
public class DataSourceRegistry {

    private final Map<String, ReportDataSource> sources = new ConcurrentHashMap<>();

    public void register(ReportDataSource source) {
        DataSourceDef def = source.def();
        if (def == null || def.getId() == null) {
            throw new IllegalArgumentException("dataSource def/id required");
        }
        sources.put(def.getId(), source);
    }

    public ReportDataSource get(String id) {
        return sources.get(id);
    }

    /** 取数据源, 不存在时抛出 (执行链路要求快速失败) */
    public ReportDataSource require(String id) {
        ReportDataSource ds = sources.get(id);
        if (ds == null) {
            throw new IllegalArgumentException("dataSource not registered: " + id);
        }
        return ds;
    }

    /** 全部已注册源 (清单端点/AI 元数据枚举用) */
    public java.util.List<ReportDataSource> all() {
        return new java.util.ArrayList<>(sources.values());
    }
}
