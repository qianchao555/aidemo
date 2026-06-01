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
