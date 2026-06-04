package com.xiaofuzi.ai.service;

import com.xiaofuzi.ai.entity.FaqEntry;
import com.xiaofuzi.ai.mapper.FaqEntryMapper;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import com.xiaofuzi.ai.dto.FaqMatchResult;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FaqService {

    private static final Logger logger = LoggerFactory.getLogger(FaqService.class);

    private final FaqEntryMapper faqEntryMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    /** 语义聚类相似度阈值：两个 query 的余弦相似度高于此值时归入同一簇 */
    private static final double CLUSTER_THRESHOLD = 0.85;
    /** 已覆盖过滤阈值：簇质心与现有 FAQ 问题相似度高于此值时视为已覆盖 */
    private static final double COVERAGE_THRESHOLD = 0.85;
    /** 批量 embedding 调用时每次传入的最大 query 数 */
    private static final int EMBEDDING_BATCH_SIZE = 50;

    public FaqService(FaqEntryMapper faqEntryMapper, ChatHistoryMapper chatHistoryMapper,
                      KnowledgeBaseService knowledgeBaseService,
                      VectorStore vectorStore,
                      EmbeddingModel embeddingModel) {
        this.faqEntryMapper = faqEntryMapper;
        this.chatHistoryMapper = chatHistoryMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    public FaqMatchResult match(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return FaqMatchResult.noMatch();
        }

        //数据库查询active状态的FAQ
        List<FaqEntry> allActive = faqEntryMapper.findAllActive();
        if (allActive.isEmpty()) {
            return FaqMatchResult.noMatch();
        }

        String normalizedQuery = normalize(userQuery);

        //用户的问题FAQ库中的问题匹配
        FaqEntry exact = allActive.stream()
                .filter(f -> normalize(f.getQuestion()).equals(normalizedQuery))
                .findFirst()
                .orElse(null);
        if (exact != null) {
            incrementHitFaq(exact);
            logger.info("FAQ 精确命中: '{}'", exact.getQuestion());
            return FaqMatchResult.hit(exact, "exact");
        }

        //模糊匹配FAQ库中的问题
        FaqEntry contains = allActive.stream()
                .filter(f -> normalize(f.getQuestion()).contains(normalizedQuery)
                        || normalizedQuery.contains(normalize(f.getQuestion())))
                .max(Comparator.comparingInt(f -> {
                    int score = 0;
                    String q = normalize(f.getQuestion());
                    if (q.equals(normalizedQuery)) score += 100;
                    if (q.contains(normalizedQuery)) score += 50;
                    if (normalizedQuery.contains(q)) score += 30;
                    return score;
                }))
                .orElse(null);
        if (contains != null) {
            incrementHitFaq(contains);
            logger.info("FAQ 模糊命中: '{}' ← query='{}'", contains.getQuestion(), userQuery);
            return FaqMatchResult.hit(contains, "fuzzy");
        }

        //关键词命中FAQ库中的问题
        FaqEntry keywordMatch = allActive.stream()
                .filter(f -> f.getKeywords() != null && !f.getKeywords().isBlank())
                .filter(f -> {
                    for (String kw : f.getKeywords().split("[,，]")) {
                        if (normalizedQuery.contains(normalize(kw))) {
                            return true;
                        }
                    }
                    return false;
                })
                .findFirst()
                .orElse(null);
        if (keywordMatch != null) {
            incrementHitFaq(keywordMatch);
            logger.info("FAQ 关键词命中: '{}'", keywordMatch.getQuestion());
            return FaqMatchResult.hit(keywordMatch, "keyword");
        }
        //未匹配FAQ库中的问题
        return FaqMatchResult.noMatch();
    }


    //系统整理场景问题及答案形成可复用知识库
    public FaqEntry create(FaqEntry entry) {
        faqEntryMapper.insert(entry);
        logger.info("FAQ 新增: id={} question='{}'", entry.getId(), entry.getQuestion());
        syncToVectorStore(entry);
        return entry;
    }

    public FaqEntry update(FaqEntry entry) {
        faqEntryMapper.update(entry);
        logger.info("FAQ 更新: id={} question='{}'", entry.getId(), entry.getQuestion());
        syncToVectorStore(entry);
        return entry;
    }

    public void delete(Long id) {
        faqEntryMapper.deleteById(id);
        logger.info("FAQ 删除(软删除): id={}", id);
    }

    public FaqEntry findById(Long id) {
        return faqEntryMapper.findById(id);
    }

    public List<FaqEntry> listAll() {
        return faqEntryMapper.findAllActive();
    }

    public List<FaqEntry> listByCategory(String category) {
        return faqEntryMapper.findByCategory(category);
    }

    public List<FaqEntry> searchByKeyword(String keyword) {
        return faqEntryMapper.searchByKeyword(keyword);
    }

    public List<FaqEntry> topHighFreq(int limit) {
        return faqEntryMapper.findTopByHitCount(limit);
    }

    /**
     * FAQ 候选挖掘（语义聚类版）：从聊天记录中提取用户提问，按语义相似度聚类，
     * 过滤已被现有 FAQ 覆盖的语义簇，返回高频候选列表。
     *
     * <p>核心流程：
     * <ol>
     *   <li>从 chat_history 提取用户提问及频次</li>
     *   <li>批量 embedding 向量化所有提问</li>
     *   <li>贪心质心聚类：按频次从高到低遍历，相似度 &gt; 阈值则归入同一簇</li>
     *   <li>每簇取最高频问题为代表，累计总频次</li>
     *   <li>与现有 FAQ 问题做语义相似度过滤，排除已覆盖的语义方向</li>
     * </ol>
     */
    public List<Map<String, Object>> getFaqCandidates(int limit, int minFrequency) {
        List<Map<String, Object>> rawFreq = chatHistoryMapper.findUserQueryFrequencies();
        if (rawFreq.isEmpty()) {
            return List.of();
        }

        // 第一步：提取 query 文本和频次，按频次降序排列
        List<QueryFreq> queryFreqs = rawFreq.stream()
                .map(row -> {
                    String query = (String) row.get("query");
                    Object cnt = row.get("cnt");
                    long freq = cnt instanceof Number ? ((Number) cnt).longValue() : 0L;
                    return new QueryFreq(query, freq);
                })
                .filter(qf -> qf.query != null && !qf.query.isBlank() && qf.freq >= minFrequency)
                .sorted(Comparator.comparingLong(QueryFreq::freq).reversed())
                .collect(Collectors.toList());

        if (queryFreqs.isEmpty()) {
            return List.of();
        }

        // 第二步：批量向量化所有 query
        List<float[]> embeddings = batchEmbed(queryFreqs);
        if (embeddings == null || embeddings.isEmpty()) {
            logger.warn("FAQ 候选挖掘: embedding 向量化失败，回退到空列表");
            return List.of();
        }

        // 第三步：贪心质心聚类
        List<SemanticCluster> clusters = greedyCluster(queryFreqs, embeddings);

        // 第四步：与现有 FAQ 做语义覆盖过滤
        List<FaqEntry> existingFaqs = faqEntryMapper.findAllActive();
        if (!existingFaqs.isEmpty()) {
            clusters = filterCoveredClusters(clusters, existingFaqs);
        }

        // 第五步：按簇总频次降序，取 top N
        clusters.sort((a, b) -> Long.compare(b.totalFreq, a.totalFreq));
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, clusters.size()); i++) {
            SemanticCluster cluster = clusters.get(i);
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("question", cluster.representative);
            candidate.put("frequency", cluster.totalFreq);
            candidates.add(candidate);
        }

        logger.info("FAQ 候选挖掘(语义聚类): 原始 {} 条 → {} 个语义簇 → 候选 {} 条",
                queryFreqs.size(), clusters.size(), candidates.size());
        return candidates;
    }

    // ---- 语义聚类内部数据结构与方法 ----

    /** query + 频次的中间数据结构 */
    private record QueryFreq(String query, long freq) {}

    /** 语义簇：包含簇内所有 query 的索引、质心向量、代表问题和总频次 */
    private static class SemanticCluster {
        final List<Integer> memberIndices = new ArrayList<>();
        float[] centroid;
        String representative;
        long totalFreq;

        SemanticCluster(int firstIdx, float[] firstVec, String query, long freq) {
            this.memberIndices.add(firstIdx);
            this.centroid = firstVec.clone(); // 初始质心 = 第一条 query 的向量
            this.representative = query;
            this.totalFreq = freq;
        }

        /** 按频次加权更新质心：new_centroid = (n * old + freq * newVec) / (n + freq) */
        void addMember(int idx, float[] vec, long freq) {
            long totalWeight = totalFreq + freq;
            for (int d = 0; d < centroid.length; d++) {
                centroid[d] = (centroid[d] * totalFreq + vec[d] * freq) / totalWeight;
            }
            memberIndices.add(idx);
            totalFreq += freq;
        }
    }

    /**
     * 批量调用 EmbeddingModel 将 query 列表转为向量。
     * 每次最多传 {@value #EMBEDDING_BATCH_SIZE} 条，避免单次请求过大。
     */
    private List<float[]> batchEmbed(List<QueryFreq> queryFreqs) {
        List<float[]> result = new ArrayList<>();
        List<String> allQueries = queryFreqs.stream().map(qf -> qf.query).collect(Collectors.toList());

        for (int i = 0; i < allQueries.size(); i += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(i + EMBEDDING_BATCH_SIZE, allQueries.size());
            List<String> batch = allQueries.subList(i, end);
            try {
                List<float[]> batchResult = embeddingModel.embed(batch);
                result.addAll(batchResult);
            } catch (Exception e) {
                logger.error("FAQ 候选挖掘: embedding 批量调用失败, batch=[{}-{}]", i, end, e);
                return null;
            }
        }
        return result;
    }

    /**
     * 贪心质心聚类：按频次从高到低遍历每条 query，
     * 与已有簇质心计算余弦相似度，高于阈值则归簇并更新质心，否则新建簇。
     */
    private List<SemanticCluster> greedyCluster(List<QueryFreq> queryFreqs, List<float[]> embeddings) {
        List<SemanticCluster> clusters = new ArrayList<>();

        for (int i = 0; i < queryFreqs.size(); i++) {
            QueryFreq qf = queryFreqs.get(i);
            float[] vec = embeddings.get(i);

            // 找相似度最高的已有簇
            int bestCluster = -1;
            double bestSim = 0;
            for (int c = 0; c < clusters.size(); c++) {
                double sim = cosineSimilarity(vec, clusters.get(c).centroid);
                if (sim > bestSim) {
                    bestSim = sim;
                    bestCluster = c;
                }
            }

            if (bestSim > CLUSTER_THRESHOLD && bestCluster >= 0) {
                // 归入已有簇，按频次加权更新质心
                clusters.get(bestCluster).addMember(i, vec, qf.freq);
            } else {
                // 相似度不足，新建簇
                clusters.add(new SemanticCluster(i, vec, qf.query, qf.freq));
            }
        }
        return clusters;
    }

    /**
     * 过滤已被现有 FAQ 覆盖的语义簇：
     * 将簇质心与每个已启用 FAQ 的问题向量做余弦相似度计算，
     * 任一 FAQ 相似度超过阈值则跳过该簇。
     */
    private List<SemanticCluster> filterCoveredClusters(List<SemanticCluster> clusters,
                                                         List<FaqEntry> existingFaqs) {
        // 批量向量化所有现有 FAQ 问题
        List<String> faqQuestions = existingFaqs.stream()
                .map(FaqEntry::getQuestion)
                .collect(Collectors.toList());
        List<float[]> faqVectors = new ArrayList<>();
        for (int i = 0; i < faqQuestions.size(); i += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(i + EMBEDDING_BATCH_SIZE, faqQuestions.size());
            List<String> batch = faqQuestions.subList(i, end);
            try {
                List<float[]> batchResult = embeddingModel.embed(batch);
                faqVectors.addAll(batchResult);
            } catch (Exception e) {
                logger.warn("FAQ 覆盖过滤: embedding 调用失败，跳过过滤步骤", e);
                return clusters; // embedding 失败时不过滤，保留全部簇
            }
        }

        List<SemanticCluster> uncovered = new ArrayList<>();
        for (SemanticCluster cluster : clusters) {
            boolean covered = false;
            for (float[] faqVec : faqVectors) {
                if (cosineSimilarity(cluster.centroid, faqVec) > COVERAGE_THRESHOLD) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                uncovered.add(cluster);
            }
        }
        logger.info("FAQ 覆盖过滤: {} 个簇 → {} 个未覆盖簇", clusters.size(), uncovered.size());
        return uncovered;
    }

    /** 计算两个向量的余弦相似度，值域 [-1, 1] */
    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不匹配: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    //高频问题，每次FAQ被命中时，增加hitCount计数，定期分析高频问题，优化FAQ库内容和结构
    private void incrementHitFaq(FaqEntry entry) {
        try {
            faqEntryMapper.incrementHitCount(entry.getId());
        } catch (Exception e) {
            logger.warn("FAQ 命中计数更新失败: id={}", entry.getId(), e);
        }
    }

    //FAQ问题和答案同步到向量数据库，形成可检索的知识库，提升RAG系统的问答能力
    private void syncToVectorStore(FaqEntry entry) {
        try {
            // 如果是更新（id 已存在），先按 faq_id 删除旧向量
            String faqIdStr = String.valueOf(entry.getId());
            vectorStore.delete(
                "faq_id == '" + faqIdStr + "' AND content_type == 'faq_entry'"
            );

            // 写入新向量
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("content_type", "faq_entry");
            metadata.put("faq_id", entry.getId());
            metadata.put("faq_category", entry.getCategory() != null ? entry.getCategory() : "");
            metadata.put("source", entry.getSourceDoc() != null ? entry.getSourceDoc() : "FAQ 标准答案");
            metadata.put("heading_path", entry.getHeadingPath() != null ? entry.getHeadingPath() : "");
            metadata.put("skip_split", true);

            String text = "【FAQ】" + entry.getQuestion() + "\n" + entry.getAnswer();
            Document doc = new Document(text, metadata);
            knowledgeBaseService.ingestParsedDocuments(List.of(doc), Map.of(), null);
            logger.info("FAQ 同步到向量库(增量): id={}", entry.getId());
        } catch (Exception e) {
            logger.warn("FAQ 同步向量库失败: id={}", entry.getId(), e);
        }
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.trim()
                .replaceAll("[？?！!。，,、；;：:　 ]", "")
                .toLowerCase();
    }
}
