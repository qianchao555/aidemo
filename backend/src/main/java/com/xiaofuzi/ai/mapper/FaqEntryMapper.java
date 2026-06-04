package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.FaqEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FaqEntryMapper {

    void insert(FaqEntry faqEntry);

    void update(FaqEntry faqEntry);

    FaqEntry findById(@Param("id") Long id);

    List<FaqEntry> findAllActive();

    List<FaqEntry> findByCategory(@Param("category") String category);

    List<FaqEntry> searchByKeyword(@Param("keyword") String keyword);

    List<FaqEntry> findTopByHitCount(@Param("limit") int limit);

    void incrementHitCount(@Param("id") Long id);

    void deleteById(@Param("id") Long id);

    List<FaqEntry> findByFilters(@Param("category") String category,
                                  @Param("status") String status,
                                  @Param("keyword") String keyword,
                                  @Param("sortBy") String sortBy,
                                  @Param("sortOrder") String sortOrder,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    long countByFilters(@Param("category") String category,
                        @Param("status") String status,
                        @Param("keyword") String keyword);

    void batchUpdateCategory(@Param("ids") List<Long> ids, @Param("category") String category);

    void batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    void batchDelete(@Param("ids") List<Long> ids);

    void updateHitCountAndTime(@Param("id") Long id);

    List<FaqEntry> findByIds(@Param("ids") List<Long> ids);

    long countTodayHits();

    List<Map<String, Object>> dailyHitTrend(@Param("days") int days);

    List<Map<String, Object>> categoryHitDistribution();
}
