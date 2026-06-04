package com.xiaofuzi.ai.controller;

import com.xiaofuzi.ai.annotation.RequireRole;
import com.xiaofuzi.ai.entity.ChatUser;
import com.xiaofuzi.ai.mapper.ChatUserMapper;
import com.xiaofuzi.ai.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    private final ChatUserMapper chatUserMapper;

    public UserController(ChatUserMapper chatUserMapper) {
        this.chatUserMapper = chatUserMapper;
    }

    /** 获取所有用户列表（仅管理员） */
    @RequireRole("admin")
    @GetMapping
    public Result<List<ChatUser>> listUsers() {
        return Result.success(chatUserMapper.findAll());
    }
}
