package com.xiaofuzi.ai.rag;

import com.xiaofuzi.ai.entity.FaqEntry;
import com.xiaofuzi.ai.mapper.FaqEntryMapper;
import com.xiaofuzi.ai.dto.FaqMatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FaqService {

    private static final Logger logger = LoggerFactory.getLogger(FaqService.class);

    private final FaqEntryMapper faqEntryMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    public FaqService(FaqEntryMapper faqEntryMapper, KnowledgeBaseService knowledgeBaseService) {
        this.faqEntryMapper = faqEntryMapper;
        this.knowledgeBaseService = knowledgeBaseService;
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
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("content_type", "faq_entry");
            metadata.put("faq_id", entry.getId());
            metadata.put("faq_category", entry.getCategory() != null ? entry.getCategory() : "");
            metadata.put("source", entry.getSourceDoc() != null ? entry.getSourceDoc() : "FAQ 标准答案");
            metadata.put("heading_path", entry.getHeadingPath() != null ? entry.getHeadingPath() : "");
            metadata.put("skip_split", true);

            String text = "【FAQ】" + entry.getQuestion() + "\n" + entry.getAnswer();
            Document doc = new Document(text, metadata);
            knowledgeBaseService.ingestParsedDocuments(List.of(doc), Map.of());
            logger.info("FAQ 同步到向量库: id={}", entry.getId());
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
