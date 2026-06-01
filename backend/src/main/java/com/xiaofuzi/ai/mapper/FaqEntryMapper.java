package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.FaqEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
}
