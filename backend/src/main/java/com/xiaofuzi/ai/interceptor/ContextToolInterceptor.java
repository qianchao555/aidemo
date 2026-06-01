package com.xiaofuzi.ai.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * 基于上下文的工具选择
 *
 * @author Chao C Qian
 * @date 2026/4/29
 */
public class ContextToolInterceptor extends ModelInterceptor {
    private final Map<String,List<ToolCallback>> roleToolMap;

    public ContextToolInterceptor(Map<String, List<ToolCallback>> roleToolMap) {
        this.roleToolMap = roleToolMap;
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        //从上下文获取角色
        String userRole=getUserRole(request);


        //根据角色选择工具
        List<ToolCallback> allTools=roleToolMap.get(userRole);


        //更新工具选项
        //TODO：根据框架API调整即可，demo为概念代码
        System.out.println("为用户角色["+userRole+"]选择工具: "+allTools);

        return handler.call(request);

    }

    private String getUserRole(ModelRequest request) {
        //TODO：实际从上下文提取用户角色
        return "user";
    }

    @Override
    public String getName() {
        return "ContextToolInterceptor";
    }
}
