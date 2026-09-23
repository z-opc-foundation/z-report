# z-report-local · 开发工作区

> z-report 的开发落地目录 (2026-09-22 起并入前的暂存工作区)
> 方案文档: `../z-report/_doc/001_方案设计.md`
>
> 约定: 本目录所有产物由开发助手维护, **不进入 z-report 正式仓 git**;
> 何时/以何形态并入正式仓由仓库负责人决定。git 提交推送由负责人自理。

---

## 模块结构

```
z-report-local/
├── pom.xml                        # 根 POM: io.github.yuku123:z-report:1.0.0-SNAPSHOT
│                                  # 独立自持 (决策④), 双 BOM: spring-boot 2.7.18 + z-boot-dependencies 1.0.2
├── z-report-common/               # 契约层: FieldType/DatasetSchema/ViewSchema/WidgetSpec/BookNodeDef/QueryResult
├── z-report-datasource/           # 数据源 SPI: ReportDataSource / DataSourceRegistry / JDBC·CSV·内存·内存SQL 四类源
│                                  # (连接池/方言/动态查询复用 z-util-jdbc)
├── z-report-dataset/              # ★ 引擎: typed 列式 Table (多键 hash join / groupby / 表达式 / 过滤)
├── z-report-render/               # 渲染: ChartOptionBuilder SPI ×5 (LINE/BAR/PIE/TABLE/KPI) → echarts option
├── z-report-book/                 # Book 树状组织 + 整体发布不可变快照 (M1 内存态)
├── z-report-ai/                   # LlmClient SPI + OpenAI 兼容实现 (决策②, M3 启用)
├── z-report-web/                  # REST: /api/datasource /api/dataset /api/view /api/render /api/book
├── z-report-spring-boot-starter/  # 自动装配 (其他 z-* 应用一行 import)
└── z-report-bootstrap/            # 独立服务启动器 (:8091)
```

## M1 已交付

- **typed Table 引擎**: STRING/INT/DOUBLE/BOOL/DATE + null 语义; 多键等值 hash join
  (INNER/LEFT, null 键不匹配, 重名列自动 `_r` 后缀); groupby 聚合 (SUM 全整型→Long);
  全表聚合 (KPI); 表达式 filter/assign (对象式 Expr DSL); pandas 风格 API
- **schema 契约**: 数据源/数据集/视图/Book 六级模型 (方案文档第三节), 单一事实源
- **渲染器后端契约**: QueryResult → 聚合 → echarts option; TABLE/KPI 为渲染器约定结构
- **端到端链路**: 注册内存源 → 注册数据集 → 明细预览 → 单 widget/整页渲染 → Book 发布/观看
  (`z-report-web/src/test/.../EndToEndFlowTest` 全链路 MockMvc 验证)
- **单测**: 引擎 (join/groupby/filter/sort/assign) + 渲染 (bar/pie/kpi) + 端到端

## 关键技术决策 (详见方案文档第四节)

1. 内建 typed 引擎, 不改造 z-util 本体; `Expr` 接口即 z-util-expr 切换缝 (M2)
2. LlmClient SPI 自研轻量; llm-gateway 剥离后经 adapter 无感切换 (M3)
3. schema 驱动通用渲染器: AI/设计器/模板三个生产者, 一个消费者
4. Maven 独立自持, 单独 clone 可编译

## M1.5 已交付 (2026-09-22, 已全量测试通过)

- [x] `z-report-datasource`: JdbcReportDataSource (Druid + mysql-connector-j, 对齐
      z-boot `z.base.db.{module}.*` 配置约定与连接保活策略; M1.6 起改走 z-util-jdbc, 见下),
      CsvReportDataSource (RFC4180
      简化解析 + 类型升级推断); SPI 增加 `tables()` 元数据探测; REST: `/api/datasource/{jdbc,csv}`, `/{id}/tables`
- [ ] `z-report-dataset`: 表达式字符串解析接 z-util-expr (**白名单受限求值, 禁止动态执行**);
      转换 DAG 编排接 z-util-workflow; 结果缓存 (z-util-cache)
- [ ] 前端工程 (React19+Antd6+Vite+echarts): UniversalRenderer 消费 `/api/render/view/{id}`

## M2/M3 冲刺已交付 (2026-09-22, 后端 51 测试全绿 + 前端 build 成功)

- [x] `ExprParser` (dataset.engine): 表达式字符串→Expr 对象树, 白名单受限求值
  (列/字面量/算术/比较/逻辑/IS NULL/IN/LIKE; 无函数调用面); computedFields 执行接入
- [x] `CachingDatasetExecutor`: 数据集结果 TTL 缓存 (默认 60s/128 条, `z.report.cache.ttl-ms` 可配; 键=schema 内容串与 id 解耦)
- [x] 发布分享 token: publish 生成, `GET /api/book/publication/{id}?token=` 强制校验
- [x] 内置模板: `POST /api/templates/sales-overview/apply` 幂等装配 demo 源/数据集/视图/书
- [x] AI 链路 (M3): AiReportService (NL→Dataset/NL→View, 契约校验本地闭环) + `/api/ai/{dataset,view}`; LLM 未配置零配置可启动, 配 `z.report.llm.api-key` 即启用
- [x] `SchemaStore` 接口化: DB 实现替换 @Component 即整体切换; DDL 见 `_doc/sql/001_schema.sql`
- [x] 前端 `_frontend/` (React19+Vite6+antd6+echarts): UniversalRenderer + Chart/Kpi/Table 三 widget + 模板/视图/分享/AI 四页; `npm run build` 验证通过

> M2 遗留未做: z-util-workflow 转换 DAG 编排; 结果缓存分布式化 (现为单机内存 TTL)。
> 详细坑清单与接手导览: `_doc/002_失败要点与坑.md`。

## M1.6 已交付 (2026-09-23, 数据源能力下沉 z-util-jdbc)

- [x] 数据源/方言/动态查询不再自研: `z-report-datasource` 依赖 `z-util-jdbc`,
      `JdbcReportDataSource` 改为 `DataSourceRegistry` (注册即探活) + `DynamicQuery` + `Dialects`,
      MySQL / PostgreSQL / H2 一套代码通吃 (原来只有 MySQL)
- [x] 属性 `url` 直连 (整条 `jdbcUrl` 优先于 `host`/`port`/`database` 拼装), `dialect` 选方言,
      `max-rows` 控单次进内存的行量级; 元数据探测走 JDBC `DatabaseMetaData`, 表清单含视图,
      列类型按 `SqlType` 归一到 `FieldType` (不再靠 MySQL 元数据 + 手写标识符白名单)
- [x] `InMemoryReportDataSource` + `DataSourceType.SQL`: 把任意已注册源 (JDBC/CSV/内存) 的表
      搬进 `z-util-expr-sql` 内存引擎, 数据单元就是一条跨源 join/group by/聚合 SELECT;
      REST `POST /api/datasource/memory-sql` (`baseTables` 声明取哪些源哪些表, `sqls` 声明数据单元)
- [x] 基表每次取数现读, 不需重注册; 源换绑/注销时旧池显式关闭 (注册表 put 即 close 旧实例)
- [x] 端到端新增: 跨源 SQL 源 → 数据集 → 预览 → widget 渲染 (`EndToEndFlowTest`)
- 注意: z-util 系 jar 传递的 `log4j-slf4j2-impl` (SLF4J 2.x 桥) 与 z-boot 的
  `log4j-slf4j-impl:2.17.2` (SLF4J 1.x 桥) 不能共存, 根 POM 对 z-util 依赖统一排掉前者

## 测试 (mvn test, 62 个全绿; 真实 MySQL 集成测试 3 个由环境变量门控)

- 引擎/渲染/端到端单测随构建执行; JDBC 链路默认由 H2 内存库覆盖 (`JdbcH2DataSourceTest`,
  无需外部实例); 真实库集成测试 (JdbcDataSourceTest) 由环境变量
  `ZREPORT_IT_DB_HOST/PORT/NAME/USER/PASSWORD` 门控, 设置后连真实 MySQL 执行
  (CI 无凭证自动跳过, 凭证不落代码库)
- z-boot 对齐版本: `z-boot.version=1.0.11` (BOM + 自家 starter, Java 8 / Spring Boot 2.7.12 基线)
- z-util 对齐版本: `z-util.version=1.0.12` (z-util-jdbc / z-util-cache / z-util-parser-csv 统一由根 POM 管)

## 并入正式仓的建议路径

1. 将本目录内容 (去掉本 README) 迁移至 `z-report/` 根
2. `_doc/001_方案设计.md` 已在正式仓中, 保持同步
3. 由负责人执行 commit/push
