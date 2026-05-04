package com.lily.spring_ai_vector.enums;

public enum JudgeResult {
    PROFANITY,
    SAFE;

    public boolean isProfanity() {
        return this == PROFANITY;
    }
}
