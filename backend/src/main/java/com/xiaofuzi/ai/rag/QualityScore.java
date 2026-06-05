package com.xiaofuzi.ai.rag;

/**
 * 检索结果质量评分 record。
 *
 * @param maxCombined 最高综合分（LLM分 × 10 + RRF分 × 100）
 * @param rrfAvg      RRF 融合分数的平均值
 * @param llmAvg      LLM 重排序分数（1-5 分）的平均值
 * @param passCount   LLM ≥ 3 且 RRF ≥ 0.01 的有效召回数量
 */
public record QualityScore(double maxCombined, double rrfAvg, double llmAvg, int passCount) {

    /** 是否通过质量门槛：至少有一条 chunk 被 LLM 评为 3 分以上且 RRF 融合排名靠前 */
    public boolean passed() {
        return passCount > 0;
    }
}
