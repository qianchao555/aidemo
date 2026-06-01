package com.xiaofuzi.ai.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型调用前，自动注入 RAG 知识上下文的 Hook
 * 这里目的是：消息预处理
 * 
 */
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class ContentRagMessageHook extends MessagesModelHook {

    private static final Logger logger = LoggerFactory.getLogger(ContentRagMessageHook.class);

    private final KnowledgeBaseService knowledgeBaseService;

    public ContentRagMessageHook(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public String getName() {
        return "ragMessageHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String userQuery = extractUserQuery(previousMessages);
        if (userQuery == null || userQuery.isEmpty()) {
            return new AgentCommand(previousMessages);
        }

        List<Document> relevantDocs = knowledgeBaseService.search(userQuery, 5);
        if (relevantDocs.isEmpty()) {
            return new AgentCommand(previousMessages);
        }

        String knowledgeContext = knowledgeBaseService.formatAsContext(relevantDocs, 3000);

        //systemPrompt：知识库搜索到的内容+xxxx
        String systemPrompt = knowledgeContext
                + "\n请结合以上知识库参考资料进行创作或回答。若参考资料与主题无关，可忽略。";

        List<Message> enrichedMessages = new ArrayList<>(previousMessages);
        enrichedMessages.add(new SystemMessage(systemPrompt));

        logger.info("RAG Hook 注入知识上下文: 命中 {} 条", relevantDocs.size());
        return new AgentCommand(enrichedMessages);
    }

    /**
     * 从消息列表中提取用户的查询文本，优先获取最后一条 UserMessage 的内容
     *
     */
    private String extractUserQuery(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                return msg.getText();
            }
        }
        return null;
    }
}
