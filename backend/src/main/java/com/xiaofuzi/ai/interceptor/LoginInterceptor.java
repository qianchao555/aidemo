package com.xiaofuzi.ai.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaofuzi.ai.annotation.RequireRole;
import com.xiaofuzi.ai.context.UserContext;
import com.xiaofuzi.ai.entity.ChatUser;
import com.xiaofuzi.ai.mapper.ChatUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Map;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoginInterceptor.class);

    private final ChatUserMapper chatUserMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoginInterceptor(ChatUserMapper chatUserMapper) {
        this.chatUserMapper = chatUserMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 放行登录接口
        if (request.getRequestURI().equals("/auth/login")) {
            return true;
        }

        // 提取并校验 token
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeJson(response, 401, "未登录");
            return false;
        }
        String token = header.substring(7);

        ChatUser user = chatUserMapper.findByToken(token);
        if (user == null) {
            writeJson(response, 401, "登录已过期，请重新登录");
            return false;
        }

        UserContext.set(user);

        // 角色权限检查
        if (handler instanceof HandlerMethod hm) {
            RequireRole requireRole = hm.getMethodAnnotation(RequireRole.class);
            if (requireRole != null) {
                String userRole = user.getRole() != null ? user.getRole() : "user";
                boolean allowed = Arrays.asList(requireRole.value()).contains(userRole);
                if (!allowed) {
                    writeJson(response, 403, "无权限");
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("status", false, "code", status, "msg", message, "data", null)));
    }
}
