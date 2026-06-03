package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.ChatUser;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatUserMapper {

    List<ChatUser> findAll();
}
