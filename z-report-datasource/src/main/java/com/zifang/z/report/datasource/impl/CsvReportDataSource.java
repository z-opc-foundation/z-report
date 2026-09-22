package com.zifang.z.report.datasource.impl;

import com.zifang.z.report.common.schema.DataSourceDef;
import com.zifang.z.report.common.schema.DataSourceType;
import com.zifang.z.report.common.schema.FieldType;
import com.zifang.z.report.dataset.engine.Table;
import com.zifang.z.report.datasource.spi.ReportDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CSV 数据源 (M1.5): 单文件单表, 首行表头, 类型按内容尽力推断。
 * <p>
 * properties:
 * - path: 本地 CSV 文件路径 (二选一)
 * - inline: 内嵌 CSV 文本 (演示/测试, 优先于 path)
 * - max-rows: 截断保护 (默认 100000)
 * <p>
 * 解析: RFC4180 简化实现 (逗号分隔, 双引号包裹, 引号内支持逗号/换行/"" 转义)。
 */
public class CsvReportDataSource implements ReportDataSource {

    private final DataSourceDef def;
    private final String tableId;
    private final LinkedHashMap<String, FieldType> colTypes;
    private final List<List<Object>> rows;

    public CsvReportDataSource(DataSourceDef def) {
        Objects.requireNonNull(def, "def");
        if (def.getType() != DataSourceType.CSV) {
            throw new IllegalArgumentException("CsvReportDataSource requires type=CSV, got: " + def.getType());
        }
        Map<String, String> p = def.getProperties() == null
                ? new LinkedHashMap<String, String>() : def.getProperties();
        String maxRows = p.getOrDefault("max-rows", "100000");
        String csv = p.get("inline");
        if (csv == null || csv.isEmpty()) {
            String path = requireProp(p, "path");
            try {
                csv = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalArgumentException("cannot read csv file: " + path + " (" + e.getMessage() + ")", e);
            }
            this.tableId = Paths.get(path).getFileName().toString().replaceAll("\\.csv$", "");
        } else {
            this.tableId = def.getId() == null ? "csv" : def.getId();
        }
        this.def = def;

        Parsed parsed = parse(csv, Integer.parseInt(maxRows));
        this.colTypes = parsed.colTypes;
        this.rows = parsed.rows;
    }

    private static String requireProp(Map<String, String> p, String key) {
        String v = p.get(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("dataSource property required: " + key);
        }
        return v;
    }

    @Override
    public DataSourceDef def() {
        return def;
    }

    @Override
    public List<String> tables() {
        return Collections.singletonList(tableId);
    }

    @Override
    public Map<String, FieldType> schema(String table) {
        requireTable(table);
        return new LinkedHashMap<>(colTypes);
    }

    @Override
    public Table read(String table) {
        requireTable(table);
        return Table.of(new ArrayList<>(colTypes.keySet()), new ArrayList<>(colTypes.values()), rows);
    }

    private void requireTable(String table) {
        if (!tableId.equals(table)) {
            throw new IllegalArgumentException("table not found in csv source [" + def.getId() + "]: " + table);
        }
    }

    // ==================== RFC4180 简化解析 ====================

    private static Parsed parse(String csv, int maxRows) {
        List<String> header = null;
        List<List<Object>> rows = new ArrayList<>();
        LinkedHashMap<String, FieldType> cols = new LinkedHashMap<>();
        for (List<String> record : new RecordScanner(csv)) {
            if (header == null) {
                header = record;
                for (String h : header) {
                    cols.put(h, FieldType.STRING);
                }
                continue;
            }
            if (rows.size() >= maxRows) {
                break;
            }
            List<Object> row = new ArrayList<>(header.size());
            for (int i = 0; i < header.size(); i++) {
                Object v = i < record.size() ? ValueInference.parse(record.get(i)) : null;
                row.add(v);
                // 类型升级: 后续行遇到更宽类型时提升 (STRING→INT/BOOL, INT→DOUBLE)
                if (v != null) {
                    FieldType cur = cols.get(header.get(i));
                    FieldType inf = ValueInference.infer(v);
                    if (shouldUpgrade(cur, inf)) {
                        cols.put(header.get(i), inf);
                    }
                }
            }
            rows.add(row);
        }
        if (header == null) {
            throw new IllegalArgumentException("empty csv (no header)");
        }
        return new Parsed(cols, rows);
    }

    /** 类型升级规则: 仅向更宽数值/具体类型单向提升 */
    private static boolean shouldUpgrade(FieldType cur, FieldType inf) {
        if (cur == FieldType.STRING) {
            return inf == FieldType.INT || inf == FieldType.DOUBLE || inf == FieldType.BOOL;
        }
        return cur == FieldType.INT && inf == FieldType.DOUBLE;
    }

    private static final class Parsed {
        final LinkedHashMap<String, FieldType> colTypes;
        final List<List<Object>> rows;

        Parsed(LinkedHashMap<String, FieldType> colTypes, List<List<Object>> rows) {
            this.colTypes = colTypes;
            this.rows = rows;
        }
    }

    /** 逐记录扫描: 处理引号内逗号/换行/"" 转义 */
    private static final class RecordScanner implements Iterable<List<String>> {
        private final String csv;

        RecordScanner(String csv) {
            this.csv = csv;
        }

        @Override
        public java.util.Iterator<List<String>> iterator() {
            return new java.util.Iterator<List<String>>() {
                private int pos = 0;
                private List<String> next = scanNext();

                private List<String> scanNext() {
                    if (pos >= csv.length()) {
                        return null;
                    }
                    List<String> record = new ArrayList<>();
                    StringBuilder field = new StringBuilder();
                    boolean inQuotes = false;
                    boolean fieldStarted = false;
                    while (pos < csv.length()) {
                        char c = csv.charAt(pos);
                        if (inQuotes) {
                            if (c == '"') {
                                if (pos + 1 < csv.length() && csv.charAt(pos + 1) == '"') {
                                    field.append('"');
                                    pos += 2;
                                    continue;
                                }
                                inQuotes = false;
                                pos++;
                                continue;
                            }
                            field.append(c);
                            pos++;
                            continue;
                        }
                        switch (c) {
                            case '"':
                                inQuotes = true;
                                fieldStarted = true;
                                pos++;
                                continue;
                            case ',':
                                record.add(fieldStarted ? field.toString() : trimOrNull(field));
                                field.setLength(0);
                                fieldStarted = false;
                                pos++;
                                continue;
                            case '\r':
                                pos++;
                                continue;
                            case '\n':
                                pos++;
                                record.add(fieldStarted ? field.toString() : trimOrNull(field));
                                return record;
                            default:
                                field.append(c);
                                fieldStarted = true;
                                pos++;
                        }
                    }
                    record.add(fieldStarted ? field.toString() : trimOrNull(field));
                    return record;
                }

                private String trimOrNull(StringBuilder sb) {
                    String s = sb.toString().trim();
                    return s.isEmpty() ? null : s;
                }

                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public List<String> next() {
                    List<String> cur = next;
                    next = scanNext();
                    return cur;
                }
            };
        }
    }
}
