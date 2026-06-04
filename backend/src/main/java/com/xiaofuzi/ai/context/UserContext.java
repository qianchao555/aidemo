package com.xiaofuzi.ai.context;

import com.xiaofuzi.ai.entity.ChatUser;

/** ThreadLocal 持有当前请求的登录用户，由 LoginInterceptor 设置，请求结束后清理 */
public final class UserContext {

    private static final ThreadLocal<ChatUser> HOLDER = new ThreadLocal<>();

    private UserContext() {}

    public static void set(ChatUser user) {
        HOLDER.set(user);
    }

    public static ChatUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
