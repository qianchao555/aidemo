package com.xiaofuzi.ai.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息过滤
 * 控制发送个LLM的消息列表，可以：
 * 1. 过滤或修改消息
 * 2. 添加上下文或者摘要
 * 3.压缩长对话
 *  请注意：
 *  这里的消息过滤只对当前这一次调用有效，
 *  不会影响整体持久存储的短期记忆，
 *  也就是下次推理调用过程不会感知到这里的过滤动作
 *
 *
 *  对于需要持久更新状态的情况
 *  可以使用ModelHook等生命周期钩子来永久更新对话历史
 *
 *
 * @author Chao C Qian
 * @date 2026/4/29
 */
public class MessageFilterInterceptor extends ModelInterceptor {
    private final int maxMessages;
    public MessageFilterInterceptor(int maxMessages) {
        this.maxMessages = maxMessages;
    }
    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        List<Message> messages = request.getMessages();
        if (messages.size() > maxMessages) {
            List<Message> filteredMessages = new ArrayList<>();

            //添加系统消息
            messages.stream()
                    .filter(m-> m instanceof SystemMessage)
                    .findFirst()
                    .ifPresent(filteredMessages::add);

            // 添加最近的消息
            int startIndex=Math.max(0,messages.size()-maxMessages+1);
            filteredMessages.addAll(messages.subList(startIndex,messages.size()));
            messages=filteredMessages;
        }

        //构建请求
        ModelRequest enhancedRequest=ModelRequest.builder(request)
                .messages(messages)
                .build();

        return handler.call(enhancedRequest);
    }

    @Override
    public String getName() {
        return "MessageFilterInterceptor";
    }
}
