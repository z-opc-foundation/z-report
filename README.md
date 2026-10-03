# z-report

> AI 驱动的拖拽式报表平台 —— 接好数据源之后，一句话就能生成报表 / 视图页 / 大屏

一人公司基座的报表中心：把「数据源 → 数据集 → 视图页 → 报表书 → 发布快照」这条链路收进一套
schema 契约（`z-report-common`），服务端只做 typed 列式计算与 echarts option 组装，渲染器不认识
任何具体图表 —— AI 生成、内置模板、拖拽设计器三个生产者共用同一个 schema 消费者。

---

## 📋 基本信息

| 字段 | 值 |
|------|-----|
| **仓库** | `z-report`（早期以 `z-report-local` 开发工作区形态存在，现已是正式仓本体，全量代码已入库） |
| **Maven 坐标** | `io.github.yuku123:z-report`（聚合 POM）+ 9 个子模块 |
| **当前版本** | `1.0.0-SNAPSHOT` —— 根 POM 里是**字面 `<version>`**，没有 `<revision>` 版本键（与 z-ctc 等已发布仓的 CI-friendly 写法不同） |
| **父项目** | `io.github.yuku123:z-boot-parent:1.0.21`（`<relativePath/>` 留空，parent 在 repo1 不在磁盘）；它继承地板 `z-boot-dependencies:1.0.20`，并 import 兄弟仓权威表 `z-boot-fleet:1.0.1`（面值 2026-09-30 从 repo1 的 parent POM 现读，实测 200） |
| **Maven Central** | **尚未发布**（实测 2026-09-30）：`z-report` 与 9 个子件的 `1.0.0-SNAPSHOT` / `1.0.0` 两条路径 + `maven-metadata.xml` 共 40 次 GET **全部 404**；本地构建产物仅供联调 / 镜像 |
| **默认端口** | `8091`（`z-report-bootstrap`；**没有** `context-path`，API 直接挂在 `/api/...`） |
| **运行口径** | Java 8 · Spring Boot 2.7.18（面值由 `z-boot-parent:1.0.21` 下发，仓根原来自起的 `java.version` / `spring-boot.version` 版本键已删） |
| **模块数** | 9 个 Maven 模块 + `_frontend/` 一个 npm 工程 |
| **最近更新** | 2026-09-30 |

---

## 🎯 能力清单

每条都能对应到代码里的类或端点（未实测到实现的能力一律不列）：

| 能力 | 实现位置 | 说明 |
|------|----------|------|
| schema 契约（单一事实源） | `z-report-common/…/schema/` 20 个类型 | `DataSourceDef` / `DatasetSchema` / `ViewSchema` / `WidgetSpec` / `BookNodeDef` / `QueryResult`；`FieldType` 五型（STRING/INT/DOUBLE/BOOL/DATE） |
| 数据源 SPI 与注册表 | `ReportDataSource` · `DataSourceRegistry` | 注册即探活，源换绑/注销时旧池显式关闭（注册表 put 即 close 旧实例） |
| 四类数据源 | `MemoryDataSource` · `JdbcReportDataSource` · `CsvReportDataSource` · `InMemoryReportDataSource` | 内存源、JDBC 源、CSV 文件源、内存 SQL 源。`DataSourceType.HTTP` 只是枚举占位，**无实现类** |
| 跨方言 JDBC | `JdbcReportDataSource` + `z-util-jdbc`（`DynamicQuery` / `Dialects` / `DatabaseMetaData`） | MySQL / PostgreSQL / H2 一套代码通吃；属性 `url` 整条直连优先于 `host`/`port`/`database` 拼装，`dialect` 选方言（默认 mysql），`max-rows` 控进内存行量级（默认 100000）；列类型经 `JdbcTypeMapping` 按 `SqlType` 归一到 `FieldType` |
| 跨源内存 SQL | `InMemoryReportDataSource`（`com.zifang.util.db.memory.InMemoryTables`） | 把任意已注册源（JDBC/CSV/内存）的表搬进内存引擎，一个数据单元就是一条跨源 join / group by / 聚合 SELECT；基表每次取数现读，不需重注册 |
| typed 列式引擎 | `z-report-dataset/…/engine/` `Table` · `Column` · `GroupBy` · `Aggs` · `Filters` · `Values` | pandas 风格 API：多键等值 hash join（INNER/LEFT，null 键不匹配，重名列自动 `_r` 后缀）、groupby 聚合、全表聚合（KPI）、filter/assign/sort |
| 表达式求值 | `ExprParser` + `Expr`（`z-util-expr-obj` 的 `ObjEngine` 承担 RAW 整形） | 表达式字符串 → Expr 对象树，**白名单受限求值**（列/字面量/算术/比较/逻辑/IS NULL/IN/LIKE，无函数调用面），不动态执行代码 |
| 结果缓存 | `CachingDatasetExecutor`（`z-util-cache`） | 数据集结果 TTL 缓存，默认 60s / 128 条，键取 schema 内容串（与 id 解耦），配置键 `z.report.cache.ttl-ms`；单机内存，未分布式化 |
| 服务端渲染 | `EChartsRenderService` + `ChartOptionBuilder` SPI ×6 | `LINE`/`BAR`/`PIE`/`TABLE`/`KPI`/`RAW` 六个 builder；`TABLE`/`KPI` 是渲染器约定结构，`RAW` 连约定结构都不要——结构由 `widget.shape` 整形程序决定（`RawOptionBuilder`） |
| 报表书与发布 | `BookService` · `InMemoryBookService` · `PublicationSnapshot` | Book 树状编排（章节/页面），`publish()` 产出不可变快照并生成分享 token，`getPublication` 强制校验 token |
| AI 生成（M3） | `AiReportService` · `LlmClient` SPI · `OpenAiCompatLlmClient` | NL→数据集 / NL→视图页两段式，产物先过本地契约校验再入库；LLM 未配置时零配置可启动（调用才抛配置指引） |
| 内置模板 | `TemplateController` | `sales-overview`（内存 demo 源 + 数据集 + 视图 + 书），apply 幂等：demo 三件按固定 id 复用，报表书每次新建 |
| schema 存储 SPI | `SchemaStore` · `InMemorySchemaStore` | 接口化收口，提供同名 DB Bean 替换 `@Component` 注册即整体切换；DDL 见 [`_doc/002_deploy/init/001_schema.sql`](_doc/002_deploy/init/001_schema.sql)（6 张表，schema 全文存 JSON 列） |
| 统一错误出口 | `ApiErrorAdvice` | 契约/整形程序不合法 → 400，状态类冲突 → 409，其余 → 500，响应体 `{ok:false, status, error}` |
| 一行接入 | [`z-report-spring-boot-starter`](z-report-spring-boot-starter/) | `ZReportAutoConfiguration` 走 `AutoConfiguration.imports`，`@ComponentScan("com.zifang.z.report")` |

---

## 🏗️ 项目结构

```
z-report/
├── pom.xml                       # 聚合 POM：parent z-boot-parent:1.0.21，version 1.0.0-SNAPSHOT
├── z-report-common/              # 契约层：schema 六级模型 + 枚举（无业务依赖）
├── z-report-dataset/             # ★ 引擎：typed 列式 Table + ExprParser + TTL 结果缓存
├── z-report-datasource/          # 数据源 SPI：JDBC / CSV / 内存 / 内存 SQL 四类源
├── z-report-render/              # WidgetSpec → echarts option（ChartOptionBuilder SPI ×6）
├── z-report-book/                # Book 树状组织 + 整体发布不可变快照（当前内存态）
├── z-report-ai/                  # LlmClient SPI + OpenAI 兼容实现 + AiReportService
├── z-report-web/                 # REST 层：7 个 Controller + SchemaStore + ApiErrorAdvice
├── z-report-spring-boot-starter/ # 自动装配（其他 z-* 应用一行 import）
├── z-report-bootstrap/           # 独立服务启动器（:8091）
├── _frontend/                    # 配套前端 npm 工程（z-report-frontend）
└── _doc/                         # 文档，见文末「文档目录」
```

依赖方向：`common` ← `dataset` ← `datasource` / `render` / `ai` ← `web` ← `starter` ← `bootstrap`。
`z-report-dataset` 不感知 `datasource`，源解析经 `TableSourceResolver` 依赖倒置注入。
**9 个模块都没有 `maven.deploy.skip`**（实测全仓 `grep deploy.skip` 零命中）——本仓目前整体未对外发布，
所以不需要按模块关掉发布；将来若要上 Central，`z-report-bootstrap` 是唯一可启动产物，需要先补 skip。

根 POM 另有两处刻意为之的写法：① 10 个自家 reactor 坐标全部钉 `${project.version}` 登记进
`dependencyManagement`，防"旧发布件被当 sibling 编进产物"（实测 fleet `1.0.1` 里至今没有任何
`z-report` 格，这一批今天顶不出差别，但它是"自家 reactor 绝不外引"的登记位）；② `log4j2` 刻意留在 `2.17.2`
（地板 `z-boot-dependencies:1.0.20` 把 api/core/jul/slf4j-impl 直接钉在 2.25.4），配套
`slf4j-api:1.7.36`，`z-util-cache` / `z-util-jdbc` 传递来的 SLF4J 2.x 桥（`log4j-slf4j2-impl`）
已在两处 POM 上按坐标 exclude——**这两座桥不能共存**，M1.6 真炸过一次 `StaticLoggerBinder` 反射初始化失败。
`flatten-maven-plugin`（1.5.0，mode `oss`）常开，因为 parent 那条 flatten 是 `inherited=false` 不下发。

---

## 🔧 技术栈

| 层级 | 技术（均来自 pom 实测） |
|------|--------------------------|
| 语言 / 运行时 | Java 8（由 `z-boot-parent` 下发，仓根 `java.version` 键已删） |
| 框架 | Spring Boot 2.7.18（同上，由父链供给） |
| 第三方版本权威 | `z-boot-parent:1.0.21` → `z-boot-dependencies:1.0.20`（地板）+ `z-boot-fleet:1.0.1`（兄弟仓表） |
| 组织内复用件 | `z-util-jdbc`（连接池/方言/动态查询/内存 SQL 引擎）· `z-util-cache` · `z-util-parser-csv` · `z-util-expr-obj` · `z-boot-datasource-starter` · `z-boot-web-starter`；仓根 `z-util.version` 键已删，面值由 fleet 下发（实测 fleet `1.0.1` 内 `z-util.version=1.0.14`，`z-util-jdbc:1.0.14` 在 repo1 为 200）。根 POM 注释里"fleet 面值 1.0.13 与本仓逐字节相同"是 parent 抬到 1.0.21 之前的旧账 |
| JSON | `jackson-databind` / `jackson-annotations`（版本走地板） |
| 日志 | log4j2 `2.17.2` + SLF4J 1.7 桥（本仓刻意与地板不同值的那一格） |
| 测试 | JUnit 5（`junit-jupiter`）+ `spring-boot-starter-test`（MockMvc）+ H2（test scope，覆盖 JDBC 链路） |
| 前端 | React 19.2 · Ant Design 6.3 · echarts 5.5 · react-router-dom 7.13 · Vite 6.2 · `@yuku123/render` ^1.0.1（`UniversalRenderer` 与该包内置 widget 由 npm registry 供给，**不在本仓自养**） |
| 构建 | Maven（后端）· npm / pnpm（前端，`_frontend/` 同时有 `package-lock.json` 与 `pnpm-lock.yaml`） |

---

## 🚀 快速开始

### 编译

```bash
mvn clean install -DskipTests
```

第三方版本一律由 `z-boot-parent:1.0.21` → `z-boot-dependencies:1.0.20` + `z-boot-fleet:1.0.0` 供给，
模块 POM 里不应再出现字面版本钉。解析不到 parent 时先确认本地仓/镜像能读到
`io.github.yuku123:z-boot-parent:1.0.21`（repo1 实测 200）。单模块构建必须带 `-am`。

### 起后端

```bash
java -jar z-report-bootstrap/target/z-report-bootstrap-1.0.0-SNAPSHOT.jar
# → http://localhost:8091/api/...
```

配置入口只有一个 [`z-report-bootstrap/src/main/resources/application.yml`](z-report-bootstrap/src/main/resources/application.yml)：
`server.port=8091`、`spring.application.name=z-report`，无 profile 分支。
`ZReportApplication` 排除了容器级 `DataSourceAutoConfiguration` 与 `DruidDataSourceAutoConfigure`
——JDBC 源的池由 `z-util-jdbc` 的注册表自建自管。

### 接 MySQL（可选，走 z-boot 约定）

配了 `z.base.db.report.*` 就会自动注册一个 id 为 `z-base-db-report` 的 JDBC 源，缺省项回退
`z.base.db.default.*`（见 `ZReportBeanConfig#jdbcDefFromZBoot`）。**所有凭据必须经环境变量注入，
禁止写进 yml / jar / 镜像层**，仓内 yml 只留占位：

| 环境变量 | 对应配置键 |
|----------|------------|
| `ZREPORT_DB_HOST` / `ZREPORT_DB_PORT` / `ZREPORT_DB_NAME` / `ZREPORT_DB_USER` / `ZREPORT_DB_PASSWORD` | `z.base.db.report.{host,port,database,username,password}` |
| `LLM_API_KEY` | `z.report.llm.api-key` 的兜底读取位（不设则 AI 端点调用即抛配置指引，其余功能照常） |

AI 相关键（`z-report-web` 的 `ZReportBeanConfig#aiReportService` 实测读取）：`z.report.llm.base-url`
（默认 `https://api.openai.com/v1`）、`z.report.llm.model`（默认 `gpt-4o-mini`）、`z.report.llm.api-key`。
缓存键：`z.report.cache.ttl-ms`（默认 `60000`）。

### 起前端

```bash
cd _frontend
npm install
ZREPORT_API_BASE=http://localhost:8091 npm run dev   # vite, 端口 5174
```

⚠ 实测坑：`_frontend/vite.config.js` 的 `/api` 代理默认目标是 `http://localhost:8080`（注释写着
"与后端 server.port 对齐"，但后端实际是 **8091**），不改 `ZREPORT_API_BASE` 会全线 404/连不上。
路由（`src/App.jsx`，HashRouter）：`/` 模板画廊、`/view/:viewId` 渲染页、`/share/:publishId` 匿名观看、
`/ai` 一句话生成。

---

## 🔌 API 一览

7 个 Controller、21 个端点，**前缀就是 `/api/...`（无 `/report` 段，也没有 context-path）**：

| 路径 | 方法 | 作用 |
|------|------|------|
| `/api/datasource` | GET | 已注册源清单（id/name/type） |
| `/api/datasource/{id}/tables` | GET | 源内数据单元清单（元数据探测，表清单含视图） |
| `/api/datasource/memory` | POST | 注册内存源 |
| `/api/datasource/jdbc` | POST | 注册 JDBC 源（`url` 或 `host`/`port`/`database`，`dialect`，`max-rows`） |
| `/api/datasource/csv` | POST | 注册 CSV 源（`path` 或 `inline`） |
| `/api/datasource/memory-sql` | POST | 注册内存 SQL 源（`baseTables` 声明取哪些源哪些表，`sqls` 声明数据单元） |
| `/api/dataset` | POST | 注册数据集定义（主源 + join + 过滤 + computedFields） |
| `/api/dataset/{id}/preview` | POST | 明细预览（返回 `QueryResult`） |
| `/api/view` | POST | 注册 `ViewSchema` |
| `/api/view/{id}` | GET | 读取视图 schema |
| `/api/render/widget` | POST | 单 widget 渲染（数据按 `widget.datasetId` 现取） |
| `/api/render/view/{viewId}` | GET | 整页渲染 → `{viewId, title, version, layout, options}` |
| `/api/book` | POST | 建报表书 |
| `/api/book/{bookId}/node` | POST | 加节点（章节/页面/挂视图） |
| `/api/book/{bookId}/tree` | GET | 读树 |
| `/api/book/{bookId}/publish` | POST | 整体发布为不可变快照（生成 token） |
| `/api/book/publication/{publishId}` | GET | 只读快照，**必须带 `?token=`** |
| `/api/ai/dataset` | POST | 一句话 → 数据集定义（`requirement` + `sourceId`） |
| `/api/ai/view` | POST | 一句话 → 视图页 schema（`requirement`，可选 `viewId`） |
| `/api/templates` | GET | 内置模板清单 |
| `/api/templates/{tplId}/apply` | POST | 幂等装配 demo 全链路并建书 |

---

## 🧪 测试

```bash
mvn test                                  # 无外部依赖即可跑
mvn test -Dtest=EndToEndFlowTest          # 全链路 MockMvc
```

实测源码统计（本仓 README 更新按作业纪律未跑 `mvn`，数字来自 `@Test` 静态清点）：**11 个测试类 /
64 个测试方法**，其中 `z-report-datasource` 的 `JdbcDataSourceTest` 3 个方法由
`@EnabledIfEnvironmentVariable(named = "ZREPORT_IT_DB_HOST")` 门控，默认跳过 ⇒ 无凭证环境实际跑 61 个。

- JDBC 链路由 H2 内存库常态覆盖（`JdbcH2DataSourceTest`），不需要外部实例。
- 真实库集成测试需设 `ZREPORT_IT_DB_HOST` / `ZREPORT_IT_DB_PORT` / `ZREPORT_IT_DB_NAME` /
  `ZREPORT_IT_DB_USER` / `ZREPORT_IT_DB_PASSWORD`，**凭证不落代码库**，CI 无凭证自动跳过。
- 端到端：`EndToEndFlowTest` 串起 注册内存源 → 数据集（含过滤）→ 视图 → 整页渲染 → Book 编排发布 →
  内存 SQL 源跨源 join → RAW 部件整形产出。

---

## 📦 构建与发布形态

本仓**没有任何部署资产**：实测无 `Dockerfile`、无 `docker-compose*.yml`、无 `deploy/`、无 `k8s/`、
无 `Makefile`，也没有 [`_doc/002_deploy/`](_doc/002_deploy/)（`_doc/` 走的是本仓自己的命名，见「文档目录」）。
对外发布同样未开：`z-report` 全家 40 次 repo1 GET 全 404（含 `maven-metadata.xml`）。
=> 目前交付方式只有「本地 `mvn install` + `java -jar` / 联调」，容器化与发版待仓库负责人补。

---

## 📄 License

许可证见仓库根 [`LICENSE`](LICENSE)（MIT License，Copyright (c) 2026 z-opc-foundation）。
注意根 POM **未**声明 `<licenses>` / `<scm>` 段——若将来要上 Maven Central，这两段是必须补的。

---

## 🗺️ 现状与未填坑

以代码为准的取舍（细节与优先级见 [`_doc/008_troubleshooting/失败要点与坑.md`](_doc/008_troubleshooting/失败要点与坑.md) 第三节）：

- **持久化未落地**：`SchemaStore` / `BookService` 现为内存实现，重启即失；DB 实现（MyBatis-Plus + JSON 列）
  只需替换同名 Bean，DDL 已备。
- **渲染端点无鉴权**：`/api/render/**`、`/api/dataset/**` 等口子全开（P-04），分享只有 publication token 一层。
- **数据源凭据明文**：REST 注册的源属性未加密（P-05）。
- **执行引擎全量内存语义**：`max-rows` 截断，非下推（P-08）；结果缓存为单机 TTL（P-02）。
- **未接入**：`z-util-wf-kernel` 转换 DAG 编排、`z-util-expr` 表达式引擎扩展缝、`DataSourceType.HTTP` 实现、
  前端拖拽编辑器（M4）。

---

## 文档目录

本仓 `_doc/` **尚未套用组织规范的 `001_arch` / `002_deploy` 目录结构**（历史遗留：文档在 z-report-local
工作区写成，随代码并入时保留了原名）。以下链接按今天的真实路径给出，未擅自搬迁：

- [`_doc/001_arch/08-design.md`](_doc/001_arch/08-design.md) — 产品定位、六级概念模型、四项关键技术决策、模块划分、数据流、分期路线（M1/M2/M3）与待定项（v1.0，2026-09-22）
- [`_doc/008_troubleshooting/失败要点与坑.md`](_doc/008_troubleshooting/失败要点与坑.md) — 已踩坑与**留给同伴的未填坑**（P-01…P-10）+ 接手 5 分钟导览；其中"代码不进 git / 正式仓零 commit / 62 个测试"等表述写作于并入之前，已过期，以本 README 与代码为准
- [`_doc/002_deploy/init/001_schema.sql`](_doc/002_deploy/init/001_schema.sql) — 落库 DDL：`report_book` / `report_book_node` / `report_data_source` / `report_dataset` / `report_view` / `report_publication` 六表，schema 全文存 JSON 列（对齐 z-lc viewconfig 模式），审计列与主键风格对齐 z-boot

配置与前端事实源另见 [`z-report-bootstrap/src/main/resources/application.yml`](z-report-bootstrap/src/main/resources/application.yml)
与 [`_frontend/`](_frontend/)。

_由 z-opc-foundation 组织维护。_
