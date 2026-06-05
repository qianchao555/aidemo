package com.xiaofuzi.ai.unit.rag;

import com.xiaofuzi.ai.rag.QualityScore;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class QualityScoreTest {

    @Test
    void shouldPassWhenPassCountGreaterThanZero() {
        QualityScore score = new QualityScore(8.5, 0.05, 4.0, 3);
        assertThat(score.passed()).isTrue();
    }

    @Test
    void shouldNotPassWhenPassCountIsZero() {
        QualityScore score = new QualityScore(2.0, 0.02, 2.0, 0);
        assertThat(score.passed()).isFalse();
    }

    @Test
    void shouldStoreAllFieldsCorrectly() {
        QualityScore score = new QualityScore(7.2, 0.08, 3.5, 5);
        assertThat(score.maxCombined()).isEqualTo(7.2);
        assertThat(score.rrfAvg()).isEqualTo(0.08);
        assertThat(score.llmAvg()).isEqualTo(3.5);
        assertThat(score.passCount()).isEqualTo(5);
    }

    @Test
    void shouldPassWhenExactlyOnePass() {
        QualityScore score = new QualityScore(5.0, 0.01, 3.0, 1);
        assertThat(score.passed()).isTrue();
    }
}
