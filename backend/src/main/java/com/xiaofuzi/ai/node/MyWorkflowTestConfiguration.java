package com.xiaofuzi.ai.node;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 集成自定义Node到StateGraph的工作流配置类
 */
@Configuration
public class MyWorkflowTestConfiguration {

    @Bean
    public StateGraph customWorkflowGraph(ChatClient.Builder chatClientBuilder) throws GraphStateException {
        //定义状态管理策略
        KeyStrategyFactory keyStrategyFactory=()->{
          Map<String, KeyStrategy> keyStrategy=new HashMap<>();
          keyStrategy.put("query",new ReplaceStrategy());
          keyStrategy.put("processedText",new ReplaceStrategy());
            //TODO：根据实际需求添加更多状态键和策略
            return keyStrategy;
        };

        //构建StateGraph
        StateGraph graph = new StateGraph(keyStrategyFactory);
        //注册自定义节点
        graph.addNode("processor", node_async(new TextProcessorNode()));
        graph.addNode("condition", node_async(new ConditionEvaluatorNode()));
        //TODO：根据实际需求添加更多节点

        //定义边（流程连接）
        graph.addEdge(StateGraph.START, "processor");
        graph.addEdge("processor", "condition");

        //条件边（根据conditionNode的结果路由）
        graph.addConditionalEdges(
                "condition",
                edge_async(
                        state ->
                                state.value("condition_result", "short").toString()
                ),
                Map.of(
                        "long", "processor",//长文本重新处理
                        "short", StateGraph.END//短文本结束流程
                )
        );
        return graph;
    }
}
