package com.xiaofuzi.ai.dto;

import com.xiaofuzi.ai.entity.FaqEntry;

public class FaqMatchResult {

    private final FaqEntry entry;
    private final String matchType;
    private final boolean matched;

    private FaqMatchResult(FaqEntry entry, String matchType, boolean matched) {
        this.entry = entry;
        this.matchType = matchType;
        this.matched = matched;
    }

    public static FaqMatchResult hit(FaqEntry entry, String type) {
        return new FaqMatchResult(entry, type, true);
    }

    public static FaqMatchResult noMatch() {
        return new FaqMatchResult(null, null, false);
    }

    public FaqEntry entry() { return entry; }

    public String matchType() { return matchType; }

    public boolean matched() { return matched; }
}
