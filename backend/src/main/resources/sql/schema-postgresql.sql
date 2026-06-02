CREATE TABLE public.xiaofuzi_knowledge_base2 (
                                                 id uuid DEFAULT uuid_generate_v4() NOT NULL,
                                                 "content" text NULL,
                                                 metadata json NULL,
                                                 embedding public.vector NULL,
                                                 CONSTRAINT xiaofuzi_knowledge_base2_pkey PRIMARY KEY (id)
);
CREATE INDEX xiaofuzi_knowledge_base2_index ON public.xiaofuzi_knowledge_base2 USING hnsw (embedding vector_cosine_ops);



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