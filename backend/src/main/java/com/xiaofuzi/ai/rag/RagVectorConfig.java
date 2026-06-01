package com.xiaofuzi.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Configuration
public class RagVectorConfig {

    private static final Logger logger = LoggerFactory.getLogger(RagVectorConfig.class);

    //Milvus向量库配置
    @Value("${spring.ai.vectorstore.milvus.host}")
    private String milvusHost;

    @Value("${spring.ai.vectorstore.milvus.port}")
    private int milvusPort;

    @Value("${spring.ai.vectorstore.milvus.database-name}")
    private String databaseName;

    @Value("${spring.ai.vectorstore.milvus.collection-name}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.milvus.embedding-dimension}")
    private int embeddingDimension;

    @Value("${spring.ai.vectorstore.milvus.initialize-schema}")
    private boolean milvusInitializeSchema;

//PG向量库配置
    @Value("${spring.ai.vectorstore.pgvector.schema-name}")
    private String schemaName;

    @Value("${spring.ai.vectorstore.pgvector.table-name}")
    private String tableName;

    @Value("${spring.ai.vectorstore.pgvector.dimensions}")
    private int dimensions;

    @Value("${spring.ai.vectorstore.pgvector.index-type}")
    private String indexType;

    @Value("${spring.ai.vectorstore.pgvector.distance-type}")
    private String distanceType;

    @Value("${spring.ai.vectorstore.pgvector.initialize-schema}")
    private boolean initializeSchema;

//    @Bean
//    public MilvusServiceClient milvusServiceClient() {
//        ConnectParam connectParam = ConnectParam.newBuilder()
//                .withHost(milvusHost)
//                .withPort(milvusPort)
//                .withDatabaseName(databaseName)
//                .build();
//        logger.info("连接 Milvus: {}:{}, 数据库: {}", milvusHost, milvusPort, databaseName);
//        return new MilvusServiceClient(connectParam);
//    }

//    @Bean("milvusVectorStore")
//    public VectorStore milvusVectorStore(MilvusServiceClient milvusServiceClient,
//                                   @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel) {
//        MilvusVectorStore vectorStore = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
//                .collectionName(collectionName)
//                .databaseName(databaseName)
//                .embeddingDimension(embeddingDimension)
//                .initializeSchema(milvusInitializeSchema)
//                .build();
//        logger.info("Milvus VectorStore 初始化完成，Collection: {}", collectionName);
//        return vectorStore;
//    }


    @Bean("vectorJdbcTemplate")
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public VectorStore vectorStore(@Qualifier("vectorJdbcTemplate")JdbcTemplate jdbcTemplate,
                                   EmbeddingModel embeddingModel) {
        PgVectorStore.PgIndexType pgIndexType = PgVectorStore.PgIndexType.valueOf(indexType);
        PgVectorStore.PgDistanceType pgDistanceType = PgVectorStore.PgDistanceType.valueOf(distanceType);

        PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName(schemaName)
                .vectorTableName(tableName)
                .dimensions(dimensions)
                .distanceType(pgDistanceType)
                .indexType(pgIndexType)
                .initializeSchema(initializeSchema)
                .build();

        logger.info("PgVector VectorStore 初始化完成，Table: {}.{}", schemaName, tableName);
        return vectorStore;
    }
}