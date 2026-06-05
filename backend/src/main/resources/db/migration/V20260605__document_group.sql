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

-- 为已有数据补齐：每条 active 文档自成一个 group
DO $$
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
END $$;

-- 已删除文档无法获取足够信息用于迁移，标记 group 后可正常管理
UPDATE knowledge_document SET is_latest = FALSE WHERE status = 'archived' AND is_latest IS NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_document_group_id ON knowledge_document(group_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_is_latest ON knowledge_document(is_latest);
