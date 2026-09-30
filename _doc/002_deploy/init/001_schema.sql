-- =====================================================================
-- z-report 落库 DDL (M2.5 同伴接手用)
-- 目标库: MySQL 8.x (z-boot 体系 z.base.db.report.* 同源即可)
-- 模式对齐: z-lc viewconfig —— schema 全文以 JSON 列存储, 结构演进零迁移
-- 命名对齐: z-boot 系列工程 (gmt_create/gmt_modified 审计列, varchar(64) 主键)
-- 实现 Hook: com.zifang.z.report.web.store.SchemaStore (DB 实现替换内存 Bean)
--           com.zifang.z.report.book.BookService (DB 实现替换 InMemoryBookService)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 报表书 (report_book 概念的根): 树状组织的容器
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_book (
    id            VARCHAR(64)  NOT NULL COMMENT '书 id (uuid)',
    title         VARCHAR(255) NOT NULL COMMENT '书名',
    description   VARCHAR(512) NULL     COMMENT '描述',
    gmt_create    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gmt_modified  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'z-report 报表书';

-- ---------------------------------------------------------------------
-- 书节点树 (邻接表): FOLDER/PAGE; PAGE 挂 view_id
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_book_node (
    id            VARCHAR(64)  NOT NULL COMMENT '节点 id',
    book_id       VARCHAR(64)  NOT NULL COMMENT '所属书 id',
    parent_id     VARCHAR(64)  NULL     COMMENT '父节点 id (根为 NULL)',
    node_type     VARCHAR(16)  NOT NULL COMMENT '节点类型: FOLDER / PAGE',
    title         VARCHAR(255) NOT NULL COMMENT '节点标题',
    order_no      INT          NOT NULL DEFAULT 0 COMMENT '同级排序 (升序)',
    view_id       VARCHAR(64)  NULL     COMMENT 'PAGE 指向的视图 id (FOLDER 为 NULL)',
    deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    gmt_create    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gmt_modified  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_node_book (book_id),
    KEY idx_node_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'z-report 书节点树';

-- ---------------------------------------------------------------------
-- 数据源注册表 (连接信息; 密码列建议落库前 AES 加密, 见坑清单)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_data_source (
    id            VARCHAR(64)  NOT NULL COMMENT '源 id',
    name          VARCHAR(255) NOT NULL COMMENT '展示名',
    type          VARCHAR(16)  NOT NULL COMMENT 'MEMORY / JDBC / CSV',
    config        JSON         NOT NULL COMMENT '连接配置 (host/port/database/username/password 等)',
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    gmt_create    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gmt_modified  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'z-report 数据源';

-- ---------------------------------------------------------------------
-- 数据集定义: DatasetSchema 全文 JSON (含 fields/joins/filters/computedFields)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_dataset (
    id            VARCHAR(64)  NOT NULL COMMENT '数据集 id',
    name          VARCHAR(255) NOT NULL COMMENT '展示名',
    source_id     VARCHAR(64)  NOT NULL COMMENT '引用数据源 id',
    base_table    VARCHAR(255) NOT NULL COMMENT '主表 (schema.baseTable)',
    schema_json   JSON         NOT NULL COMMENT 'DatasetSchema 全文 (com.zifang.z.report.common.schema.DatasetSchema)',
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    gmt_create    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gmt_modified  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ds_source (source_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'z-report 数据集定义';

-- ---------------------------------------------------------------------
-- 视图页定义: ViewSchema 全文 JSON (widgets + layout)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_view (
    id            VARCHAR(64)  NOT NULL COMMENT '视图 id',
    title         VARCHAR(255) NOT NULL COMMENT '视图标题',
    version       INT          NOT NULL DEFAULT 1 COMMENT 'schema 版本 (乐观锁用)',
    schema_json   JSON         NOT NULL COMMENT 'ViewSchema 全文 (com.zifang.z.report.common.schema.ViewSchema)',
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    gmt_create    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gmt_modified  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'z-report 视图页';

-- ---------------------------------------------------------------------
-- 发布快照: 整书不可变快照 + 分享 token (观看侧入口)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_publication (
    publish_id    VARCHAR(64)  NOT NULL COMMENT '发布 id',
    book_id       VARCHAR(64)  NOT NULL COMMENT '来源书 id',
    share_token   VARCHAR(64)  NOT NULL COMMENT '分享 token (uuid 去横线, 观看侧必须携带)',
    publish_time  DATETIME(3)  NOT NULL COMMENT '发布时间',
    nodes_json    JSON         NOT NULL COMMENT '发布时节点树快照 (List<BookNodeDef> 不可变)',
    gmt_create    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (publish_id),
    KEY idx_pub_book (book_id),
    KEY idx_pub_token (share_token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'z-report 发布快照';

-- =====================================================================
-- 实现注意 (同伴接手):
-- 1. schema_json 反序列化目标类已在 common 模块 (Jackson 注解齐备, 直接 ObjectMapper)
-- 2. SchemaStore 的语义约定: require* 抛 IllegalArgumentException / get* 返 null /
--    put* upsert; DB 实现必须遵守, 渲染主链路依赖该语义
-- 3. 节点树 order 字段 = order_no (order 是 MySQL 保留字)
-- 4. report_publication 无 gmt_modified: 快照不可变, 覆盖发布 = 新 publish_id
-- 5. 数据源 config 列含密码: 生产必须应用层加密 (z-boot 是否有现成 KMS/加解密组件
--    请先调研, 见 _doc/002_失败要点与坑.md P-05)
-- =====================================================================
