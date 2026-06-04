-- 用户认证与权限 migration
ALTER TABLE chat_user
  ADD COLUMN IF NOT EXISTS password_hash VARCHAR(256),
  ADD COLUMN IF NOT EXISTS auth_token    VARCHAR(64),
  ADD COLUMN IF NOT EXISTS role          VARCHAR(32) NOT NULL DEFAULT 'user';

UPDATE chat_user SET role = 'admin' WHERE username = 'zhangsan';
