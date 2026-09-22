package com.zifang.z.report.dataset;

import com.zifang.z.report.common.schema.ComputedField;
import com.zifang.z.report.common.schema.DatasetSchema;
import com.zifang.z.report.common.schema.JoinDef;
import com.zifang.z.report.common.schema.QueryResult;
import com.zifang.z.report.common.schema.WidgetFilter;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带 TTL 的数据集结果缓存执行器 (M2): 组合模式包装 DatasetExecutor,
 * 相同 schema 定义在 TTL 窗口内直接返回上次 QueryResult, 不重复执行源读取/join/计算。
 * <p>
 * 设计要点:
 * - 缓存键 = schema 内容稳定串 (sourceId/baseTable/joins/filters/computedFields),
 *   与 schema.id 解耦 —— 同一数据集改定义立即视为新键, 不会串结果
 * - 返回的 QueryResult 为共享实例 (调用方只读约定; 深拷贝成本高, M2 取性能),
 *   渲染链路与 HTTP 序列化均为只读消费, 满足约定
 * - 过期条目惰性清理 (读取时判断 + 定期 sweeping), 无外部时钟依赖, 可测试
 * - TTL &lt;= 0 或容量 0 时等价直通 (不缓存)
 * <p>
 * M2 内存版: 单机 ConcurrentHashMap; 分布式缓存 (z-cache 接入) 见 _doc/002 坑清单。
 */
public class CachingDatasetExecutor extends DatasetExecutor {

    /** 默认 TTL: 60s */
    public static final long DEFAULT_TTL_MS = 60_000L;
    /** 默认容量上限: 128 个数据集定义 */
    public static final int DEFAULT_MAX_ENTRIES = 128;

    private final long ttlMs;
    private final int maxEntries;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    private static final class Entry {
        final QueryResult result;
        final long expireAt;

        Entry(QueryResult result, long expireAt) {
            this.result = result;
            this.expireAt = expireAt;
        }
    }

    public CachingDatasetExecutor(TableSourceResolver sources) {
        this(sources, DEFAULT_TTL_MS, DEFAULT_MAX_ENTRIES);
    }

    public CachingDatasetExecutor(TableSourceResolver sources, long ttlMs, int maxEntries) {
        super(sources);
        this.ttlMs = ttlMs;
        this.maxEntries = Math.max(0, maxEntries);
    }

    @Override
    public QueryResult execute(DatasetSchema schema) {
        if (ttlMs <= 0 || maxEntries == 0) {
            return super.execute(schema);
        }
        String key = cacheKey(schema);
        long now = System.currentTimeMillis();
        Entry hit = cache.get(key);
        if (hit != null && now < hit.expireAt) {
            return hit.result;
        }
        QueryResult fresh = super.execute(schema);
        sweepIfNeeded(now);
        cache.put(key, new Entry(fresh, now + ttlMs));
        return fresh;
    }

    /** 清空缓存 (源数据变更/测试用) */
    public void invalidateAll() {
        cache.clear();
    }

    /** 惰性淘汰: 过期条目直接删; 超容量时先清过期再粗暴清空 (M2 简化, 无 LRU) */
    private void sweepIfNeeded(long now) {
        if (cache.size() < maxEntries) {
            if (cache.size() % 16 == 0) {
                Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator();
                while (it.hasNext()) {
                    if (now >= it.next().getValue().expireAt) {
                        it.remove();
                    }
                }
            }
            return;
        }
        boolean removed = false;
        Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue().expireAt) {
                it.remove();
                removed = true;
            }
        }
        if (!removed) {
            cache.clear(); // 全部仍在 TTL 内却超容量: 简化处理, 直接清空重算
        }
    }

    /** schema 内容稳定键 (与 id/name 解耦) */
    static String cacheKey(DatasetSchema s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.getSourceId()).append('|').append(s.getBaseTable());
        List<JoinDef> joins = s.getJoins();
        if (joins != null) {
            for (JoinDef j : joins) {
                sb.append("|J:").append(j.getSourceId()).append('.').append(j.getTable())
                        .append(':').append(j.getType())
                        .append(':').append(java.util.Arrays.toString(j.getLeftKeys()))
                        .append('>').append(java.util.Arrays.toString(j.getRightKeys()));
            }
        }
        List<WidgetFilter> filters = s.getFilters();
        if (filters != null) {
            for (WidgetFilter f : filters) {
                sb.append("|F:").append(f.getField()).append(f.getOp())
                        .append('=').append(f.getValue()).append(',').append(f.getValues());
            }
        }
        List<ComputedField> computed = s.getComputedFields();
        if (computed != null) {
            for (ComputedField c : computed) {
                sb.append("|C:").append(c.getName()).append(':').append(c.getType())
                        .append(':').append(c.getExpr());
            }
        }
        return sb.toString();
    }
}
