package com.zifang.z.report.datasource.impl;

import com.alibaba.druid.pool.DruidDataSource;
import com.zifang.z.report.common.schema.DataSourceDef;
import com.zifang.z.report.common.schema.DataSourceType;
import com.zifang.z.report.common.schema.FieldType;
import com.zifang.z.report.dataset.engine.Table;
import com.zifang.z.report.datasource.spi.ReportDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JDBC (MySQL) 数据源。
 * <p>
 * 连接池与配置约定对齐 z-boot (z-boot-datasource-starter.ModuleDataSourceTemplate):
 * - Druid 连接池 + testWhileIdle/keepAlive 保活 (远程 RDS 空闲连接防杀)
 * - URL: jdbc:mysql://host:port/database?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8
 * - driver: com.mysql.cj.jdbc.Driver (com.mysql:mysql-connector-j, 版本由 BOM 锁定)
 * <p>
 * 安全约定:
 * - 只做 SELECT *; 表标识符白名单校验 (JdbcTypeMapping.isSafeIdentifier)
 * - max-rows 属性截断全量读取 (默认 100000, M1 内存语义保护)
 * - 凭证由部署方注入 (REST 注册/配置中心), 不落代码库
 */
public class JdbcReportDataSource implements ReportDataSource, AutoCloseable {

    public static final String PROP_HOST = "host";
    public static final String PROP_PORT = "port";
    public static final String PROP_DATABASE = "database";
    public static final String PROP_USERNAME = "username";
    public static final String PROP_PASSWORD = "password";
    public static final String PROP_MAX_ROWS = "max-rows";

    private final DataSourceDef def;
    private final DruidDataSource pool;
    private final int maxRows;

    public JdbcReportDataSource(DataSourceDef def) {
        Objects.requireNonNull(def, "def");
        if (def.getType() != DataSourceType.JDBC) {
            throw new IllegalArgumentException("JdbcReportDataSource requires type=JDBC, got: " + def.getType());
        }
        Map<String, String> p = def.getProperties() == null
                ? new LinkedHashMap<String, String>() : def.getProperties();
        String host = requireProp(p, PROP_HOST);
        String port = p.getOrDefault(PROP_PORT, "3306");
        String database = requireProp(p, PROP_DATABASE);
        String username = requireProp(p, PROP_USERNAME);
        String password = p.getOrDefault(PROP_PASSWORD, "");
        this.maxRows = Integer.parseInt(p.getOrDefault(PROP_MAX_ROWS, "100000"));
        this.def = def;

        this.pool = new DruidDataSource();
        this.pool.setUrl(String.format(
                "jdbc:mysql://%s:%s/%s?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8",
                host, port, database));
        this.pool.setUsername(username);
        this.pool.setPassword(password);
        this.pool.setDriverClassName("com.mysql.cj.jdbc.Driver");
        // 对齐 z-boot 连接保活策略 (远程 RDS 空闲连接防杀)
        this.pool.setInitialSize(1);
        this.pool.setMinIdle(1);
        this.pool.setMaxActive(8);
        this.pool.setTestWhileIdle(true);
        this.pool.setTimeBetweenEvictionRunsMillis(30000L);
        this.pool.setMinEvictableIdleTimeMillis(60000L);
        this.pool.setKeepAlive(true);
        this.pool.setValidationQuery("SELECT 1");
        this.pool.setValidationQueryTimeout(3000);
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
        List<String> names = new ArrayList<>();
        try (Connection conn = pool.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            return names;
        } catch (Exception e) {
            throw new IllegalStateException("list tables failed on source [" + def.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, FieldType> schema(String table) {
        String sql = "SELECT * FROM " + quoted(table) + " LIMIT 1";
        try (Connection conn = pool.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            Map<String, FieldType> cols = new LinkedHashMap<>();
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                cols.put(md.getColumnLabel(i), JdbcTypeMapping.toFieldType(md.getColumnType(i), md.getColumnTypeName(i)));
            }
            return cols;
        } catch (Exception e) {
            throw new IllegalStateException("schema probe failed on [" + def.getId() + "." + table + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public Table read(String table) {
        String sql = "SELECT * FROM " + quoted(table);
        try (Connection conn = pool.getConnection();
             Statement st = conn.createStatement()) {
            st.setMaxRows(maxRows);
            try (ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<String> names = new ArrayList<>(n);
                List<FieldType> types = new ArrayList<>(n);
                for (int i = 1; i <= n; i++) {
                    names.add(md.getColumnLabel(i));
                    types.add(JdbcTypeMapping.toFieldType(md.getColumnType(i), md.getColumnTypeName(i)));
                }
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(n);
                    for (int i = 1; i <= n; i++) {
                        row.add(JdbcTypeMapping.normalize(rs.getObject(i)));
                    }
                    rows.add(row);
                }
                return Table.of(names, types, rows);
            }
        } catch (Exception e) {
            throw new IllegalStateException("read failed on [" + def.getId() + "." + table + "]: " + e.getMessage(), e);
        }
    }

    /** 反引号包裹标识符 (白名单校验先行) */
    private static String quoted(String table) {
        if (!JdbcTypeMapping.isSafeIdentifier(table)) {
            throw new IllegalArgumentException("unsafe table identifier: " + table);
        }
        int dot = table.indexOf('.');
        if (dot > 0) {
            return "`" + table.substring(0, dot) + "`.`" + table.substring(dot + 1) + "`";
        }
        return "`" + table + "`";
    }

    @Override
    public void close() {
        pool.close();
    }
}
