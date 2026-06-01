package com.xiaofuzi.ai.interceptor;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * System Prompt：基于状态的动态提示
 * 创建一个模型拦截器，根据对话长度调整系统提示
 *
 * @author Chao C Qian
 * @date 2026/4/29
 */
public class StateAwarePromptInterceptor extends ModelInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(StateAwarePromptInterceptor.class);
    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        List<Message> messages = request.getMessages();
        int messageCount = messages.size();
        //基础提示
        String basePrompt = "你是一个有用的助手";
        if(messageCount>10){
            basePrompt+="这是一个长对话-请尽量保持精准简洁。";
        }

        //更新系统消息
        SystemMessage enhancedSystemMessage;
        if(request.getSystemMessage()==null){
            enhancedSystemMessage=new SystemMessage(basePrompt);
        }else {
            enhancedSystemMessage=new  SystemMessage(
                    request.getSystemMessage().getText()+""+basePrompt
            );
        }

        //创建增强的请求
        ModelRequest enhancedModelRequest=ModelRequest.builder(request)
                .systemMessage(enhancedSystemMessage)
                .build();
        //调用处理器
        ModelResponse modelResponse = handler.call(enhancedModelRequest);
        logger.info("modelResponse:{}",modelResponse);
        return modelResponse;
    }

    @Override
    public String getName() {
        return "StateAwarePromptInterceptor";
    }
}
//使用拦截器创建Agent
//ReactAgent agent=ReactAgent.builder()
//        .name("context_aware_agent")
//        .model(chatModel)
//        .interceptors(new StateAwarePromptInterceptor())
//        .build();
