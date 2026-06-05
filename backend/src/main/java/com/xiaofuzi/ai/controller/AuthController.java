package com.xiaofuzi.ai.controller;

import com.xiaofuzi.ai.context.UserContext;
import com.xiaofuzi.ai.dto.LoginRequest;
import com.xiaofuzi.ai.dto.LoginResponse;
import com.xiaofuzi.ai.entity.ChatUser;
import com.xiaofuzi.ai.mapper.ChatUserMapper;
import com.xiaofuzi.ai.vo.Result;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final ChatUserMapper chatUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.init.default-role:user}")
    private String defaultRole;

    @Value("${app.init.default-department:全公司}")
    private String defaultDepartment;

    public AuthController(ChatUserMapper chatUserMapper) {
        this.chatUserMapper = chatUserMapper;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        ChatUser user = chatUserMapper.findByUsername(request.getUsername());
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return Result.error("用户名或密码错误");
        }

        String token = com.xiaofuzi.ai.util.AppConstants.uuidNoDash();
        chatUserMapper.updateToken(user.getId(), token);

        LoginResponse resp = LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole() != null ? user.getRole() : defaultRole)
                .department(user.getDepartment() != null ? user.getDepartment() : defaultDepartment)
                .token(token)
                .build();

        logger.info("用户登录: username={}, role={}", user.getUsername(), resp.getRole());
        return Result.success(resp);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        ChatUser user = UserContext.get();
        if (user != null) {
            chatUserMapper.clearToken(user.getAuthToken());
            logger.info("用户登出: username={}", user.getUsername());
        }
        return Result.success();
    }

    @GetMapping("/me")
    public Result<LoginResponse> me() {
        ChatUser user = UserContext.get();
        if (user == null) {
            return Result.error("未登录");
        }
        LoginResponse resp = LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole() != null ? user.getRole() : defaultRole)
                .department(user.getDepartment() != null ? user.getDepartment() : defaultDepartment)
                .token(user.getAuthToken())
                .build();
        return Result.success(resp);
    }
}
