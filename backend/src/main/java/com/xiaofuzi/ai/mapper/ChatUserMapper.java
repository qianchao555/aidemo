package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.ChatUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatUserMapper {

    List<ChatUser> findAll();

    ChatUser findByUsername(@Param("username") String username);

    ChatUser findByToken(@Param("token") String token);

    void updateToken(@Param("id") Long id, @Param("token") String token);

    void clearToken(@Param("token") String token);

    void updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    List<ChatUser> findByPasswordHashIsNull();

    void updateRole(@Param("id") Long id, @Param("role") String role);

    void updateDepartment(@Param("id") Long id, @Param("department") String department);
}
