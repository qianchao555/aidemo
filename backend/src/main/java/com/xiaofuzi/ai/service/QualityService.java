package com.xiaofuzi.ai.service;

import com.xiaofuzi.ai.dto.quality.*;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QualityService {

    private static final Logger logger = LoggerFactory.getLogger(QualityService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatHistoryMapper chatHistoryMapper;

    public QualityService(ChatHistoryMapper chatHistoryMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
    }

    public QualityOverview getOverview() {
        Map<String, Object> row = chatHistoryMapper.getRatingDistribution();
        return QualityOverview.builder()
                .totalAnswers(toLong(row.get("total_assistant")))
                .thumbsUp(toLong(row.get("thumbs_up")))
                .thumbsDown(toLong(row.get("thumbs_down")))
                .unrated(toLong(row.get("unrated")))
                .satisfactionRate(toDouble(row.get("satisfaction_rate")))
                .build();
    }

    public List<DailyRatingTrendItem> getDailyRatingTrend(int days) {
        List<Map<String, Object>> rows = chatHistoryMapper.dailyRatingTrend(days);
        return rows.stream().map(row -> DailyRatingTrendItem.builder()
                .day((String) row.get("day"))
                .thumbsUp(toLong(row.get("thumbs_up")))
                .thumbsDown(toLong(row.get("thumbs_down")))
                .unrated(toLong(row.get("unrated")))
                .satisfactionRate(toDouble(row.get("satisfaction_rate")))
                .build()
        ).collect(Collectors.toList());
    }

    public List<LowRatedMessage> getLowRatedMessages(int limit) {
        List<Map<String, Object>> rows = chatHistoryMapper.findLowRatedMessages(limit);
        return rows.stream().map(row -> LowRatedMessage.builder()
                .id(toLong(row.get("id")))
                .threadId((String) row.get("thread_id"))
                .userQuestion((String) row.get("user_question"))
                .assistantAnswerExcerpt((String) row.get("assistant_answer_excerpt"))
                .assistantAnswerFull((String) row.get("assistant_answer_full"))
                .sourceDoc((String) row.get("source_doc"))
                .headingPath((String) row.get("heading_path"))
                .createTime(formatTime(row.get("create_time")))
                .rating((Integer) row.get("rating"))
                .build()
        ).collect(Collectors.toList());
    }

    public List<BlindSpotItem> getBlindSpots(int limit) {
        List<Map<String, Object>> rows = chatHistoryMapper.findBlindSpots(limit);
        return rows.stream().map(row -> BlindSpotItem.builder()
                .sourceDoc((String) row.get("source_doc"))
                .headingPath((String) row.get("heading_path"))
                .negativeCount(toLong(row.get("negative_count")))
                .lastOccurrence(formatTime(row.get("last_occurrence")))
                .build()
        ).collect(Collectors.toList());
    }

    public List<DepartmentQualityItem> getDepartmentStats() {
        List<Map<String, Object>> rows = chatHistoryMapper.departmentRatingStats();
        return rows.stream().map(row -> DepartmentQualityItem.builder()
                .department((String) row.get("department"))
                .totalRated(toLong(row.get("total_rated")))
                .thumbsUp(toLong(row.get("thumbs_up")))
                .thumbsDown(toLong(row.get("thumbs_down")))
                .satisfactionRate(toDouble(row.get("satisfaction_rate")))
                .build()
        ).collect(Collectors.toList());
    }

    private long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        return 0L;
    }

    private Double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) return Double.parseDouble(s);
        return null;
    }

    private String formatTime(Object v) {
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().format(FMT);
        if (v instanceof LocalDateTime ldt) return ldt.format(FMT);
        if (v != null) return v.toString();
        return null;
    }
}
