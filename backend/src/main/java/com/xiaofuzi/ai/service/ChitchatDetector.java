package com.xiaofuzi.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 闲聊意图检测器。
 *
 * <p>在 FAQ 未命中后、Agent 交互前，快速判断用户输入是否为闲聊，
 * 避免闲聊走完整知识库检索链路造成资源浪费和生硬的兜底回复。
 *
 * <p>检测策略：关键词精确匹配 + 短文本特征判断，零延迟零成本。
 */
@Component
public class ChitchatDetector {

    private static final Logger logger = LoggerFactory.getLogger(ChitchatDetector.class);

    private static final int MAX_CHITCHAT_LENGTH = 15;

    private static final Pattern PURE_PUNCTUATION = Pattern.compile("^[？?!！。，,、；;：:　 \\s]+$");
    private static final Pattern PURE_EMOJI = Pattern.compile("^[😂😊😄🙂😉😍🥰😘😋😜🤪😎🤩🥳👍👎🙏💪✌️🤞👋🎉❤️💔🔥⭐]+$");
    private static final Pattern ONLY_LAUGH = Pattern.compile("^[哈呵嘿嘻嘿嗯哦啊唉呦哟]{1,10}$");

    private static final Set<String> GREETINGS = Set.of(
            "你好", "您好", "hi", "hello", "嗨", "hey", "在吗", "在不在",
            "早上好", "下午好", "晚上好", "早安", "午安", "晚安",
            "大家好", "你们好"
    );

    private static final Set<String> FAREWELLS = Set.of(
            "再见", "拜拜", "bye", "88", "回头见", "下次聊", "先这样",
            "谢谢", "多谢", "感谢", "thanks", "thank you", "3q",
            "不客气", "没事", "没关系"
    );

    private static final Set<String> SMALL_TALK = Set.of(
            "今天天气", "天气怎么样", "天气如何", "下雨", "好热", "好冷",
            "吃饭了吗", "吃了吗", "中午吃啥", "晚上吃啥",
            "你是谁", "你叫什么", "你的名字", "你是机器人吗", "你是AI吗",
            "你会什么", "你能做什么", "你有什么功能",
            "讲个笑话", "说个笑话", "来个笑话", "讲个故事",
            "无聊", "好无聊", "聊聊天", "陪我聊天",
            "今天星期几", "几点了", "现在几点", "今天几号"
    );

    private static final Set<String> SHORT_ACK = Set.of(
            "好的", "ok", "OK", "行", "可以", "嗯", "哦", "好", "对",
            "是的", "没错", "知道了", "明白了", "懂了", "了解",
            "哈哈", "呵呵", "嘿嘿"
    );

    public enum ChitchatType {
        GREETING, FAREWELL, SMALL_TALK, SHORT_ACK, NONE
    }

    public record ChitchatResult(boolean isChitchat, ChitchatType type) {
        public static ChitchatResult none() {
            return new ChitchatResult(false, ChitchatType.NONE);
        }

        public static ChitchatResult of(ChitchatType type) {
            return new ChitchatResult(true, type);
        }
    }

    public ChitchatResult detect(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return ChitchatResult.none();
        }

        String normalized = userQuery.trim();

        if (PURE_PUNCTUATION.matcher(normalized).matches()
                || PURE_EMOJI.matcher(normalized).matches()) {
            logger.debug("闲聊检测: 纯标点/表情 | query='{}'", userQuery);
            return ChitchatResult.of(ChitchatType.SHORT_ACK);
        }

        if (ONLY_LAUGH.matcher(normalized).matches()) {
            logger.debug("闲聊检测: 纯语气词 | query='{}'", userQuery);
            return ChitchatResult.of(ChitchatType.SHORT_ACK);
        }

        if (normalized.length() <= 2 && SHORT_ACK.contains(normalized)) {
            logger.debug("闲聊检测: 短确认词 | query='{}'", userQuery);
            return ChitchatResult.of(ChitchatType.SHORT_ACK);
        }

        if (GREETINGS.contains(normalized)) {
            logger.debug("闲聊检测: 问候语 | query='{}'", userQuery);
            return ChitchatResult.of(ChitchatType.GREETING);
        }

        if (FAREWELLS.contains(normalized)) {
            logger.debug("闲聊检测: 告别/感谢 | query='{}'", userQuery);
            return ChitchatResult.of(ChitchatType.FAREWELL);
        }

        for (String phrase : SMALL_TALK) {
            if (normalized.contains(phrase)) {
                logger.debug("闲聊检测: 日常闲聊 | query='{}'", userQuery);
                return ChitchatResult.of(ChitchatType.SMALL_TALK);
            }
        }

        if (normalized.length() <= MAX_CHITCHAT_LENGTH && !containsBusinessKeyword(normalized)) {
            logger.debug("闲聊检测: 短文本无业务关键词 | query='{}'", userQuery);
            return ChitchatResult.of(ChitchatType.SHORT_ACK);
        }

        return ChitchatResult.none();
    }

    private boolean containsBusinessKeyword(String text) {
        String lower = text.toLowerCase();
        return lower.contains("请假") || lower.contains("报销") || lower.contains("加班")
                || lower.contains("离职") || lower.contains("入职") || lower.contains("转正")
                || lower.contains("调岗") || lower.contains("年假") || lower.contains("病假")
                || lower.contains("产假") || lower.contains("婚假") || lower.contains("调休")
                || lower.contains("工资") || lower.contains("社保") || lower.contains("公积金")
                || lower.contains("医保") || lower.contains("养老") || lower.contains("失业")
                || lower.contains("流程") || lower.contains("制度") || lower.contains("规定")
                || lower.contains("申请") || lower.contains("审批") || lower.contains("材料")
                || lower.contains("考核") || lower.contains("绩效") || lower.contains("合同")
                || lower.contains("福利") || lower.contains("补贴") || lower.contains("培训")
                || lower.contains("出差") || lower.contains("考勤") || lower.contains("打卡")
                || lower.contains("证明") || lower.contains("标准") || lower.contains("天数")
                || lower.contains("额度") || lower.contains("津贴");
    }
}
