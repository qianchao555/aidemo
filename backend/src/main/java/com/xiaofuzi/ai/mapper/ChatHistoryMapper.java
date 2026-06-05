package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatHistoryMapper {

    void insert(ChatHistory chatHistory);

    List<ChatHistory> findByThreadId(@Param("threadId") String threadId);

    //当前线程的limit条消息
    List<ChatHistory> findRecentByThreadId(@Param("threadId") String threadId,
                                           @Param("limit") int limit);

    void deleteByThreadId(@Param("threadId") String threadId);

    List<ChatHistory> findAllThreadIds();

    List<Map<String, Object>> findUserQueryFrequencies();

    void updateRating(@Param("id") Long id, @Param("rating") Integer rating);

    Map<String, Object> getRatingDistribution();

    List<Map<String, Object>> dailyRatingTrend(@Param("days") int days);

    List<Map<String, Object>> findLowRatedMessages(@Param("limit") int limit);

    List<Map<String, Object>> findBlindSpots(@Param("limit") int limit);

    List<Map<String, Object>> departmentRatingStats();
}
