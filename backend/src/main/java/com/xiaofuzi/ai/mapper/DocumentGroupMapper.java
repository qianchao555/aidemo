package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.DocumentGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentGroupMapper {

    void insert(DocumentGroup group);

    DocumentGroup findById(@Param("id") Long id);

    List<DocumentGroup> findByDepartment(@Param("department") String department);

    void updateLatestDocument(@Param("id") Long id, @Param("latestDocumentId") Long latestDocumentId);

    void updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 查询某 group 下所有文档（含 archived），用于获取 available_versions */
    List<DocumentGroup> findAllActive();
}
