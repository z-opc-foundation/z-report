package com.zifang.z.report.dataset.engine;

import com.zifang.z.report.common.schema.AggType;

import java.util.List;

/**
 * 聚合实现 (输入为剔除 null 后的值序列)。
 * SUM: 全整型 → Long, 含浮点 → Double; AVG → Double; MIN/MAX → 原类型。
 */
final class Aggs {

    private Aggs() {
    }

    static Object aggregate(AggType type, List<Object> values) {
        if (values.isEmpty()) {
            return null; // 空组聚合为 null (COUNT 不会走到这里, 由 GroupBy 直接算)
        }
        switch (type) {
            case COUNT:
                return (long) values.size();
            case SUM:
                return sum(values);
            case AVG:
                return avg(values);
            case MIN:
                return extremum(values, true);
            case MAX:
                return extremum(values, false);
            default:
                return null;
        }
    }

    private static Object sum(List<Object> values) {
        boolean allInt = true;
        double acc = 0;
        for (Object v : values) {
            if (!Values.isInteger(v)) {
                allInt = false;
            }
            acc += Values.toDouble(v);
        }
        // 注意: 不可写 allInt ? (long) acc : acc —— 三元数值提升会把 Long 分支吞成 Double
        if (allInt) {
            return (long) acc;
        }
        return acc;
    }

    private static Object avg(List<Object> values) {
        double acc = 0;
        for (Object v : values) {
            acc += Values.toDouble(v);
        }
        return acc / values.size();
    }

    private static Object extremum(List<Object> values, boolean min) {
        Object best = values.get(0);
        for (Object v : values) {
            int c = Values.compare(v, best);
            if (min ? c < 0 : c > 0) {
                best = v;
            }
        }
        return best;
    }
}
