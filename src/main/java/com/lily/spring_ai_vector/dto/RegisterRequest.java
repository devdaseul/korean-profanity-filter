package com.lily.spring_ai_vector.dto;

import com.lily.spring_ai_vector.enums.Category;

import java.util.List;

/**
 * 관리자 비속어 등록 요청 DTO
 *
 * category, severity, wordType은 선택값 — null/빈값이면 기본값으로 보정
 */
public record RegisterRequest(
        List<String> texts,
        List<Long> logIds,
        String category,
        Integer severity,
        String wordType
) {
    public RegisterRequest {
        if (category == null || category.isBlank()) category = Category.PROFANITY.name();
        if (severity == null || severity < 1 || severity > 3) severity = 1;
        if (wordType == null || wordType.isBlank()) wordType = "WORD";
    }
}