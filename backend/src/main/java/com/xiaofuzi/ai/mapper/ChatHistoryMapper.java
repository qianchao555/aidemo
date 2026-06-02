package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatHistoryMapper {

    void insert(ChatHistory chatHistory);

    List<ChatHistory> findByThreadId(@Param("threadId") String threadId);

    //当前线程的limit条消息
    List<ChatHistory> findRecentByThreadId(@Param("threadId") String threadId,
                                           @Param("limit") int limit);

    void deleteByThreadId(@Param("threadId") String threadId);

    List<ChatHistory> findAllThreadIds();
}
