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
