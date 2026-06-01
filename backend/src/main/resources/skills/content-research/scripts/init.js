// Content Research Skill 初始化脚本
// 调研方法论配置（领域知识，不绑定工具）

(function () {
  'use strict';

  var BEHAVIOR = {
    defaultDepth: 'standard',
    defaultStrictness: 'strict',
    defaultCompetitorCount: 4,
    defaultTimeRange: '3years',
    defaultReportType: 'analysis'
  };

  var DEPTH_CONFIG = {
    basic: {
      maxSources: 3,
      detailLevel: 'overview',
      sections: ['概述', '关键信息', '参考资料']
    },
    standard: {
      maxSources: 8,
      detailLevel: 'detailed',
      sections: ['概述', '背景', '现状与数据', '关键参与者', '参考资料']
    },
    deep: {
      maxSources: 15,
      detailLevel: 'comprehensive',
      sections: ['概述', '背景', '技术细节', '产业链', '竞争格局', '前沿动态', '参考资料']
    }
  };

  var FACT_CHECK_CONFIDENCE = {
    true: { min: 80, label: '可信' },
    partially_true: { min: 50, label: '部分可信' },
    false: { min: 0, label: '不可信' },
    unverifiable: { label: '无法验证' }
  };

  console.log('[content-research] 技能初始化完成');
  console.log('[content-research] 默认调研深度: ' + BEHAVIOR.defaultDepth);
  console.log('[content-research] 核查严格度: ' + BEHAVIOR.defaultStrictness);
  console.log('[content-research] 调研配置已加载: ' + Object.keys(DEPTH_CONFIG).length + ' 个深度级别');
})();