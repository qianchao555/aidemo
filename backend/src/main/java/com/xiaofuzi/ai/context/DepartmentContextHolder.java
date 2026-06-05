package com.xiaofuzi.ai.context;

/** ThreadLocal 持有当前请求的部门上下文，由 AgentController 设置，请求结束后清理 */
public final class DepartmentContextHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private DepartmentContextHolder() {}

    public static void set(String department) {
        if (department != null && !department.isBlank()) {
            HOLDER.set(department);
        }
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
