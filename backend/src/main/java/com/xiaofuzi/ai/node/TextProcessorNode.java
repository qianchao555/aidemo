package com.xiaofuzi.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.HashMap;
import java.util.Map;

public class TextProcessorNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 从状态中获取输入文本
        String input=state.value("query","").toString();
        //执行业务逻辑，例如文本清洗、分词、情感分析等
        String processedText="Processed: "+input; //示例处理结果
        //将处理结果存回状态，供后续节点使用
        Map<String, Object> result=new HashMap<>();
        result.put("processedText",processedText);
        return result;
    }
}
