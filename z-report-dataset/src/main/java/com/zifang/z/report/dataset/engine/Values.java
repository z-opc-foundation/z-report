package com.zifang.z.report.dataset.engine;

/**
 * 值工具: 跨类型比较 / 数值化。
 * 引擎内部共用, 规则与 Expr 的比较语义一致。
 */
final class Values {

    private Values() {
    }

    /**
     * 跨类型比较:
     * 1) 双方为 Number → 数值比较
     * 2) 同类型 Comparable → 自然序
     * 3) 其他 → 字符串形式比较 (M1 宽松兜底)
     */
    static int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a.getClass() == b.getClass() && a instanceof Comparable) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            int c = ((Comparable) a).compareTo(b);
            return c;
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    /** 数值化: Number 直接取值, 数字字面量字符串可解析, 其余 null */
    static Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof Boolean) {
            return (Boolean) v ? 1.0 : 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isInteger(Object v) {
        return v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long;
    }
}
