package com.xiaofuzi.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * 条件评估节点示例
 * 用于工作流中的条件分支判断
 *
 * @author Chao C Qian
 * @date 2026/4/29
 */
public class ConditionEvaluatorNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value("input", "").toString();

        //根据输入内容，决定路由
        String route;
        if(input.contains("错误")){
            route="error_Handling";
        }else if(input.contains("数据")) {
            route = "data_Processing";
        }else {
            route="default";
        }
        Map<String, Object> result=new HashMap<>();
        result.put("condition_result",route);
        return result;
    }
}
