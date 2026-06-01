package com.xiaofuzi.ai.controller;

import com.xiaofuzi.ai.dto.ContentChatRequest;
import com.xiaofuzi.ai.rag.FaqService;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import com.xiaofuzi.ai.service.RagQaAgentService;
import com.xiaofuzi.ai.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    private final RagQaAgentService ragQaAgentService;

    public AgentController(RagQaAgentService ragQaAgentService) {
        this.ragQaAgentService = ragQaAgentService;
    }


    /***********************************RAG 知识库问答 Agent*******************************/

    @PostMapping("/rag-qa/chat")
    public Result<String> ragQaChat(@RequestBody ContentChatRequest contentChatRequest) {
        String message = contentChatRequest.getUserMessage();
        String threadId = contentChatRequest.getThreadId();
        String response = ragQaAgentService.ask(threadId, message);
        return Result.success(response);
    }
}
