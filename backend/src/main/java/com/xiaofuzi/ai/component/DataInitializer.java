package com.xiaofuzi.ai.component;

import com.xiaofuzi.ai.entity.ChatUser;
import com.xiaofuzi.ai.mapper.ChatUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时初始化用户数据：
 * 1. 为缺少密码哈希的用户设置默认密码 123456
 * 2. 确保 zhangsan 拥有 admin 角色
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final ChatUserMapper chatUserMapper;

    @Value("${app.init.default-admin:zhangsan}")
    private String defaultAdmin;

    @Value("${app.init.default-password:123456}")
    private String defaultPassword;

    @Value("${app.init.default-department:全公司}")
    private String defaultDepartment;

    public DataInitializer(ChatUserMapper chatUserMapper) {
        this.chatUserMapper = chatUserMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 1. 初始化密码
        List<ChatUser> users = chatUserMapper.findByPasswordHashIsNull();
        for (ChatUser user : users) {
            String hash = encoder.encode(defaultPassword);
            chatUserMapper.updatePasswordHash(user.getId(), hash);
            logger.info("用户密码初始化: username={}", user.getUsername());
        }
        if (!users.isEmpty()) {
            logger.info("密码初始化完成，共 {} 个用户，默认密码: {}", users.size(), defaultPassword);
        }

        // 2. 确保 admin 角色正确设置（不依赖 migration SQL）
        ChatUser admin = chatUserMapper.findByUsername(defaultAdmin);
        if (admin != null && !com.xiaofuzi.ai.util.AppConstants.ROLE_ADMIN.equals(admin.getRole())) {
            chatUserMapper.updateRole(admin.getId(), com.xiaofuzi.ai.util.AppConstants.ROLE_ADMIN);
            logger.info("已将 {} 的角色设置为 admin", defaultAdmin);
        }

        // 3. 确保 admin 有默认部门
        if (admin != null && admin.getDepartment() == null) {
            chatUserMapper.updateDepartment(admin.getId(), defaultDepartment);
            logger.info("已将 {} 的部门设置为 {}", defaultAdmin, defaultDepartment);
        }
    }
}
