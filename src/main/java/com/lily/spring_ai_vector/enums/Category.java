package com.lily.spring_ai_vector.enums;

/** 비속어 유형 분류 */
public enum Category {
    PROFANITY,      // 일반 욕설
    ABUSE,          // 혐오·비하
    SEXUAL,         // 성적 표현
    HATE,           // 차별·증오
    UNCATEGORIZED;  // 미분류

    /** 문자열 → enum 변환. 매핑 실패 시 UNCATEGORIZED 반환 */
    public static Category fromString(String value) {
        if (value == null) return UNCATEGORIZED;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNCATEGORIZED;
        }
    }
}
