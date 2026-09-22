package com.zifang.z.report.datasource.impl;

import com.zifang.z.report.common.schema.FieldType;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * JDBC 元数据 → 引擎 FieldType 映射 + 行值归一化。
 * <p>
 * 归一化约定 (引擎 Table 宽松存取的值域):
 * - 整型 → Long; 浮点/DECIMAL → 原样 (BigDecimal/Double, 引擎按 Number 数值化)
 * - DATE → LocalDate (FieldType.DATE 语义)
 * - DATETIME/TIMESTAMP/TIME → ISO 字符串 (STRING 语义, M1 不引入 LocalDateTime 维度)
 * - BLOB/BINARY → "[binary]" 占位
 */
final class JdbcTypeMapping {

    private JdbcTypeMapping() {
    }

    static FieldType toFieldType(int sqlType, String typeName) {
        switch (sqlType) {
            case java.sql.Types.INTEGER:
            case java.sql.Types.BIGINT:
            case java.sql.Types.SMALLINT:
            case java.sql.Types.TINYINT:
                return FieldType.INT;
            case java.sql.Types.DECIMAL:
            case java.sql.Types.NUMERIC:
            case java.sql.Types.FLOAT:
            case java.sql.Types.REAL:
            case java.sql.Types.DOUBLE:
                return FieldType.DOUBLE;
            case java.sql.Types.BOOLEAN:
            case java.sql.Types.BIT:
                return FieldType.BOOL;
            case java.sql.Types.DATE:
                return FieldType.DATE;
            default:
                // VARCHAR/CHAR/TEXT/DATETIME/TIMESTAMP/TIME/JSON/ENUM... 一律 STRING
                return FieldType.STRING;
        }
    }

    /** ResultSet 取出的原始值 → 引擎值域 */
    static Object normalize(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate) {
            return v; // mysql-connector-j DATE 已返回 LocalDate
        }
        if (v instanceof Date) {
            return ((Date) v).toLocalDate();
        }
        if (v instanceof Timestamp) {
            return v.toString();
        }
        if (v instanceof java.sql.Time) {
            return v.toString();
        }
        if (v instanceof byte[]) {
            return "[binary]";
        }
        if (v instanceof BigDecimal) {
            return v; // Number 子类, 引擎按数值处理
        }
        if (v instanceof java.time.LocalDateTime) {
            return v.toString();
        }
        return v;
    }

    /** 标识符白名单: 表名/schema.table (防注入第二道防线, 第一道是只读 SELECT) */
    static boolean isSafeIdentifier(String name) {
        if (name == null || name.isEmpty() || name.length() > 128) {
            return false;
        }
        return name.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?");
    }
}
