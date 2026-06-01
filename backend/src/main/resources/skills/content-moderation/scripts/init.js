// Content Moderation Skill 初始化脚本
// 审查规则和评分标准（领域知识配置，不绑定工具）

(function () {
  'use strict';

  var BEHAVIOR = {
    defaultStrictness: 'medium',
    defaultReplaceMode: 'mask'
  };

  var RISK_LEVELS = {
    low: { threshold: 30, action: 'pass' },
    medium: { threshold: 60, action: 'review' },
    high: { threshold: 80, action: 'review' },
    critical: { threshold: 100, action: 'fail' }
  };

  var SCORE_THRESHOLDS = {
    readability: { pass: 75, review: 60, fail: 40 },
    comprehensive: { pass: 80, conditional: 60, fail: 40 }
  };

  console.log('[content-moderation] 技能初始化完成');
  console.log('[content-moderation] 默认严格度: ' + BEHAVIOR.defaultStrictness);
  console.log('[content-moderation] 风险等级数: ' + Object.keys(RISK_LEVELS).length);
  console.log('[content-moderation] 评分标准已加载');
})();