package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeDocumentMapper {

    void insert(KnowledgeDocument doc);

    KnowledgeDocument findById(@Param("id") Long id);

    List<KnowledgeDocument> findAllActive();

    List<KnowledgeDocument> findByCategory(@Param("category") String category);

    List<KnowledgeDocument> findByFilters(@Param("category") String category,
                                          @Param("status") String status,
                                          @Param("keyword") String keyword,
                                          @Param("department") String department,
                                          @Param("sortBy") String sortBy,
                                          @Param("sortOrder") String sortOrder,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    long countByFilters(@Param("category") String category,
                        @Param("status") String status,
                        @Param("keyword") String keyword,
                        @Param("department") String department);

    void update(KnowledgeDocument doc);

    void softDelete(@Param("id") Long id);

    long countActive();

    long sumChunks();

    List<Map<String, Object>> countByCategory();
}
