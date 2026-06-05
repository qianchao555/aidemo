package com.xiaofuzi.ai.unit.util;

import com.xiaofuzi.ai.vo.Result;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResultTest {

    @Test
    void successWithDataShouldSetStatusTrue() {
        Result<String> r = Result.success("hello");
        assertThat(r.isStatus()).isTrue();
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMsg()).isEqualTo("success");
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    void successWithoutDataShouldSetStatusTrue() {
        Result<Void> r = Result.success();
        assertThat(r.isStatus()).isTrue();
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isNull();
    }

    @Test
    void errorShouldSetStatusFalse() {
        Result<Void> r = Result.error();
        assertThat(r.isStatus()).isFalse();
        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).isEqualTo("error");
    }

    @Test
    void errorWithMessageShouldUseMessage() {
        Result<Void> r = Result.error("自定义错误");
        assertThat(r.isStatus()).isFalse();
        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).isEqualTo("自定义错误");
    }
}
