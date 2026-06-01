package com.xiaofuzi.ai.rag;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 意图识别
 */
public enum IntentType {

    POLICY_QA(
            "制度问答",
            Pattern.compile(".*(?:制度|规定|条例|政策|办法|规范|标准).*(?:是什么|是什么内容|怎么规定|如何规定|有哪些).*"),
            Pattern.compile(".*第[一二三四五六七八九十百千万]+[章节条].*"),
            5, 0.0,
            "逐条列出制度条款，注明出处，结构清晰"
    ),

    PROCESS_GUIDE(
            "流程指引",
            Pattern.compile(".*(?:怎么[做办弄]|如何|怎样|流程|步骤|手续|途径|方法).*"),
            Pattern.compile(".*(?:申请|提交|审批|办理|处理|操作|执行).*"),
            3, 0.0,
            "按步骤分点说明流程，标注每个步骤的依据"
    ),

    DEFINITION(
            "定义查询",
            Pattern.compile(".*(?:什么是|什么叫|是指|定义为|指的是|含义|定义|概念).*"),
            null,
            3, 0.0,
            "先给出简洁定义，再展开说明，标注出处"
    ),

    COMPARISON(
            "对比查询",
            Pattern.compile(".*(?:区别|对比|不同|哪个好|优劣|异同|比较|差别).*"),
            null,
            5, 0.0,
            "用对比结构呈现，分别标注出处"
    ),

    SCOPE_CHECK(
            "范围确认",
            Pattern.compile(".*(?:是否包括|是否包含|是否适用|适用于|适用于谁|谁可以|范围|对象|哪些[人部门]).*"),
            null,
            3, 0.0,
            "明确适用范围或对象，引用条款原文"
    ),

    VIOLATION(
            "违规后果",
            Pattern.compile(".*(?:违反|违规|处罚|惩罚|处分|后果|责任|罚款|警告).*"),
            null,
            3, 0.0,
            "列出违规情形及对应措施，标注出处"
    ),

    CHITCHAT(
            "闲聊/无关",
            null,
            null,
            0, 0.0,
            "引导用户提出与知识库相关的问题"
    );

    private final String displayName;
    private final Pattern primaryPattern;
    private final Pattern secondaryPattern;
    private final int topK;
    private final double threshold;
    private final String answerStrategy;

    IntentType(String displayName, Pattern primaryPattern, Pattern secondaryPattern,
               int topK, double threshold, String answerStrategy) {
        this.displayName = displayName;
        this.primaryPattern = primaryPattern;
        this.secondaryPattern = secondaryPattern;
        this.topK = topK;
        this.threshold = threshold;
        this.answerStrategy = answerStrategy;
    }

    public boolean matches(String query) {
        if (primaryPattern == null) {
            return false;
        }
        return primaryPattern.matcher(query).matches()
                || (secondaryPattern != null && secondaryPattern.matcher(query).matches());
    }

    /**
     * 意图归类
     */
    public static IntentType classify(String query) {
        if (query == null || query.isBlank()) {
            return CHITCHAT;
        }
        String normalized = query.trim();
        for (IntentType intent : values()) {
            if (intent != CHITCHAT && intent.matches(normalized)) {
                return intent;
            }
        }
        return CHITCHAT;
    }

    public String getDisplayName() { return displayName; }

    public int getTopK() { return topK; }

    public double getThreshold() { return threshold; }

    public String getAnswerStrategy() { return answerStrategy; }

    public boolean isChitchat() { return this == CHITCHAT; }

    public static final List<IntentType> ANSWERABLE_TYPES = List.of(
            POLICY_QA, PROCESS_GUIDE, DEFINITION, COMPARISON, SCOPE_CHECK, VIOLATION
    );
}
