package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    void insert(ChatSession session);

    ChatSession findByThreadId(@Param("threadId") String threadId);

    List<ChatSession> findByUserId(@Param("userId") Long userId);

    void update(ChatSession session);

    void deleteByThreadId(@Param("threadId") String threadId);
}
