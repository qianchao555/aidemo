CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 启用 pg_trgm 扩展（三元组模糊匹配，用于关键词检索）
CREATE EXTENSION IF NOT EXISTS pg_trgm;


CREATE TABLE IF NOT EXISTS chat_history (
                                            id          BIGSERIAL PRIMARY KEY,
                                            thread_id   VARCHAR(64)  NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    content     TEXT         NOT NULL,
    source_doc  VARCHAR(512),
    heading_path VARCHAR(1024),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_chat_history_thread_id ON chat_history(thread_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_create_time ON chat_history(thread_id, create_time);

CREATE TABLE IF NOT EXISTS faq_entry (
                                         id           BIGSERIAL PRIMARY KEY,
                                         question     VARCHAR(512)  NOT NULL,
    answer       TEXT          NOT NULL,
    keywords     VARCHAR(512),
    category     VARCHAR(64),
    source_doc   VARCHAR(512),
    heading_path VARCHAR(1024),
    hit_count    INTEGER       NOT NULL DEFAULT 0,
    status       VARCHAR(16)   NOT NULL DEFAULT 'active',
    create_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_faq_entry_question ON faq_entry(question);
CREATE INDEX IF NOT EXISTS idx_faq_entry_category ON faq_entry(category);
CREATE INDEX IF NOT EXISTS idx_faq_entry_hit_count ON faq_entry(hit_count DESC);
CREATE INDEX IF NOT EXISTS idx_faq_entry_status ON faq_entry(status);




-- 文档元信息管理表
CREATE TABLE IF NOT EXISTS knowledge_document (
                                                  id              BIGSERIAL PRIMARY KEY,
                                                  document_name   VARCHAR(512)  NOT NULL,
    document_type   VARCHAR(32)   NOT NULL,
    file_path       VARCHAR(1024),
    file_size       BIGINT,
    category        VARCHAR(64),
    department      VARCHAR(128),
    version         VARCHAR(32)  DEFAULT '1.0',
    effective_date  DATE,
    description     VARCHAR(512),
    chunk_count     INT         DEFAULT 0,
    status          VARCHAR(16) DEFAULT 'active',
    create_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_kd_status ON knowledge_document(status);
CREATE INDEX IF NOT EXISTS idx_kd_category ON knowledge_document(category);


-- 向量表与文档表关联：增加 document_id 列
ALTER TABLE xiaofuzi_knowledge_base_v2
    ADD COLUMN IF NOT EXISTS document_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_vector_document_id
    ON xiaofuzi_knowledge_base_v2(document_id);


-- 三元组索引，用于关键词模糊匹配（中文适用，按字符三元组切分）
CREATE INDEX IF NOT EXISTS idx_vector_content_trgm
    ON xiaofuzi_knowledge_base_v2 USING gin (content gin_trgm_ops);




-- 会话列表管理表
CREATE TABLE IF NOT EXISTS chat_session (
                                            id            BIGSERIAL PRIMARY KEY,
                                            thread_id     VARCHAR(64)  NOT NULL UNIQUE,
    user_id       BIGINT,
    title         VARCHAR(256) NOT NULL DEFAULT '新对话',
    message_count INT          NOT NULL DEFAULT 0,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
CREATE INDEX IF NOT EXISTS idx_cs_thread_id ON chat_session(thread_id);
CREATE INDEX IF NOT EXISTS idx_cs_user_id ON chat_session(user_id);



CREATE TABLE IF NOT EXISTS chat_user (
                                         id           BIGSERIAL PRIMARY KEY,
                                         username     VARCHAR(64)  NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- 预置模拟用户（仅当表为空时插入）
INSERT INTO chat_user (username, display_name)
SELECT * FROM (VALUES
                   ('zhangsan', '张三'),
                   ('lisi', '李四'),
                   ('wangwu', '王五')
              ) AS t(username, display_name)
WHERE NOT EXISTS (SELECT 1 FROM chat_user);

-- 用户认证与权限 migration
ALTER TABLE chat_user
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(256),
    ADD COLUMN IF NOT EXISTS auth_token    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS role          VARCHAR(32) NOT NULL DEFAULT 'user';

UPDATE chat_user SET role = 'admin' WHERE username = 'zhangsan';


ALTER TABLE faq_entry ADD COLUMN IF NOT EXISTS last_hit_time TIMESTAMP;
COMMENT ON COLUMN faq_entry.last_hit_time IS '最近一次命中时间';


-- 用户表添加部门字段
ALTER TABLE chat_user ADD COLUMN IF NOT EXISTS department VARCHAR(64) DEFAULT '全公司';

-- 存量文档 NULL 部门设默认值
UPDATE knowledge_document SET department = '全公司' WHERE department IS NULL;

-- 知识文档表部门字段加索引
CREATE INDEX IF NOT EXISTS idx_kd_dept ON knowledge_document(department);

-- 初始化 zhangsan 的部门
UPDATE chat_user SET department = '全公司' WHERE username = 'zhangsan' AND department IS NULL;

-- 聊天记录添加评分字段
ALTER TABLE chat_history ADD COLUMN IF NOT EXISTS rating SMALLINT DEFAULT NULL;


-- 创建文档组表
CREATE TABLE IF NOT EXISTS document_group (
                                              id                  BIGSERIAL PRIMARY KEY,
                                              name                VARCHAR(255) NOT NULL,
    latest_document_id  BIGINT,
    department          VARCHAR(100),
    status              VARCHAR(20) DEFAULT 'active',
    create_time         TIMESTAMP DEFAULT NOW(),
    update_time         TIMESTAMP DEFAULT NOW()
    );

-- knowledge_document 新增版本追溯列
ALTER TABLE knowledge_document
    ADD COLUMN IF NOT EXISTS group_id  BIGINT,
    ADD COLUMN IF NOT EXISTS is_latest BOOLEAN DEFAULT TRUE;



-- 已删除文档无法获取足够信息用于迁移，标记 group 后可正常管理
UPDATE knowledge_document SET is_latest = FALSE WHERE status = 'archived' AND is_latest IS NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_document_group_id ON knowledge_document(group_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_is_latest ON knowledge_document(is_latest);



select * from faq_entry;
select * from xiaofuzi_knowledge_base_v2 xkbv ;
SELECT * from knowledge_document kd ;


select * from chat_user;

select * from chat_session;
select * from chat_history ch ;





DECLARE
r RECORD;
    gid BIGINT;
BEGIN
FOR r IN SELECT id, document_name, department FROM knowledge_document WHERE status = 'active' AND group_id IS NULL
    LOOP
         INSERT INTO document_group (name, latest_document_id, department)
         VALUES (r.document_name, r.id, r.department)
             RETURNING id INTO gid;
UPDATE knowledge_document SET group_id = gid, is_latest = TRUE WHERE id = r.id;
END LOOP;
END;



