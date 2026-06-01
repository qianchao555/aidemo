package com.xiaofuzi.ai.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化输出控制模型响应格式
 *
 * @author Chao C Qian
 * @date 2026/4/29
 */

//也可以在Interceptor中动态调整
public class DynamicFormatInterceptor extends ModelInterceptor{


    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        //根据请求内容，决定输出格式
        String outputSchema=determineOutputSchema(request);

        //在消息中添加格式说明
        List<Message> updateMessages=addFormatInstructions(
                request.getMessages(),
                outputSchema
        );

        return handler.call(request);
    }

    private List<Message> addFormatInstructions(List<Message> messages, String outputSchema) {
        //TODO:实现格式说明
        return messages;
    }

    private String determineOutputSchema(ModelRequest request) {
        //TODO:实现输出格式，大多数为json、md
        return "";
    }

    @Override
    public String getName() {
        return "DynamicFormatInterceptor";
    }
}
