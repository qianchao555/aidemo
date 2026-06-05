package com.xiaofuzi.ai.util;

import java.util.UUID;

/** 应用级共享常量与工具方法 */
public final class AppConstants {

    private AppConstants() {}

    /** 实体状态：生效中 */
    public static final String STATUS_ACTIVE = "active";

    /** 生成无连字符的 UUID 字符串 */
    public static String uuidNoDash() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
