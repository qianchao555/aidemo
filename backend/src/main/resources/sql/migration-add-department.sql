-- 用户表添加部门字段
ALTER TABLE chat_user ADD COLUMN IF NOT EXISTS department VARCHAR(64) DEFAULT '全公司';

-- 存量文档 NULL 部门设默认值
UPDATE knowledge_document SET department = '全公司' WHERE department IS NULL;

-- 知识文档表部门字段加索引
CREATE INDEX IF NOT EXISTS idx_kd_dept ON knowledge_document(department);

-- 初始化 zhangsan 的部门
UPDATE chat_user SET department = '全公司' WHERE username = 'zhangsan' AND department IS NULL;
