package com.xiaofuzi.ai.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

/**
 * 动态模型选择
 * 根据任务复杂度或用户偏好，动态选择模型
 *
 * @author Chao C Qian
 * @date 2026/4/29
 */
public class DynamicModelInterceptor extends ModelInterceptor {
    private final ChatModel simpleModel;
    private final ChatModel complexModel;

    public DynamicModelInterceptor(ChatModel simpleModel, ChatModel complexModel) {
        this.simpleModel = simpleModel;
        this.complexModel = complexModel;
    }


    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        //分析任务复杂程度
        boolean isComplexTask=analyzeComplexTask(request.getMessages());

        //选择合适的模型
        ChatModel selectedModel=isComplexTask?complexModel:simpleModel;

        //TODO:实际开发中，可能还需要在Agent级别切换模型


        return handler.call(request);
    }

    private boolean analyzeComplexTask(List<Message> messages) {
        //TODO：实际分析消息内容，判断任务复杂度
        return messages.size()>3;
    }

    @Override
    public String getName() {
        return "DynamicModelInterceptor";
    }
}
