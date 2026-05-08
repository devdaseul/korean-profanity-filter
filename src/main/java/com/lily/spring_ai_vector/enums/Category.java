package com.lily.spring_ai_vector.enums;

/** 비속어 유형 분류 */
public enum Category {
    PROFANITY("욕설"),
    ABUSE("모욕"),
    SEXUAL("성적표현"),
    HATE("차별"),
    UNCATEGORIZED("미분류");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 문자열 → enum 변환. 매핑 실패 시 UNCATEGORIZED 반환 */
    public static Category fromString(String value) {
        if (value == null || value.isBlank()) return UNCATEGORIZED;
        
        String trimmed = value.trim();
        for (Category category : values()) {
            // 1. 영문(Enum name) 완전 일치 확인
            if (category.name().equalsIgnoreCase(trimmed)) {
                return category;
            }
            // 2. 한글 라벨 완전 일치 확인 (부분 일치로 인한 버그 방지)
            if (category.getLabel().equals(trimmed)) {
                return category;
            }
        }
        return UNCATEGORIZED;
    }
}
