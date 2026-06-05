package com.xiaofuzi.ai.util;

import java.util.UUID;

/** 应用级共享常量与工具方法 */
public final class AppConstants {

    private AppConstants() {}

    /** 实体状态：生效中 */
    public static final String STATUS_ACTIVE = "active";

    /** 角色：管理员 */
    public static final String ROLE_ADMIN = "admin";
    /** 角色：普通用户 */
    public static final String ROLE_USER = "user";

    /** 聊天角色：用户 */
    public static final String CHAT_ROLE_USER = "user";
    /** 聊天角色：助手 */
    public static final String CHAT_ROLE_ASSISTANT = "assistant";
    /** 聊天角色：系统 */
    public static final String CHAT_ROLE_SYSTEM = "system";

    /** 元数据 key：内容类型 */
    public static final String META_CONTENT_TYPE = "content_type";
    /** 元数据 key：文档分类 */
    public static final String META_DOCUMENT_CATEGORY = "document_category";
    /** 元数据 key：来源文档名 */
    public static final String META_SOURCE = "source";
    /** 元数据 key：章节路径 */
    public static final String META_HEADING_PATH = "heading_path";
    /** 元数据 key：步骤标题 */
    public static final String META_STEP_TITLE = "step_title";
    /** 元数据 key：文档 ID */
    public static final String META_DOCUMENT_ID = "document_id";
    /** 元数据 key：文档组 ID */
    public static final String META_GROUP_ID = "group_id";
    /** 元数据 key：版本号 */
    public static final String META_VERSION = "version";
    /** 元数据 key：是否最新版本 */
    public static final String META_IS_LATEST = "is_latest";
    /** 元数据 key：跳过切分标记 */
    public static final String META_SKIP_SPLIT = "skip_split";
    /** 内容类型值：FAQ 条目 */
    public static final String CONTENT_TYPE_FAQ = "faq_entry";
    /** 内容类型值：PDF 文档 */
    public static final String CONTENT_TYPE_PDF = "pdf_document";
    /** 内容类型值：Word 文档 */
    public static final String CONTENT_TYPE_WORD = "word_document";
    /** 内容类型值：文本文档 */
    public static final String CONTENT_TYPE_TXT = "text_document";
    /** 内容类型值：通用文档 */
    public static final String CONTENT_TYPE_GENERIC = "generic_document";
    /** 内容类型值：未知 */
    public static final String CONTENT_TYPE_UNKNOWN = "unknown";

    /** 生成无连字符的 UUID 字符串 */
    public static String uuidNoDash() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
