package com.xiaofuzi.ai.hook;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

public class HumanInTheLoopHook {

    //配置检查点保存器（人工介入需要检查点来处理中断）
    MemorySaver saver=new MemorySaver();


}
