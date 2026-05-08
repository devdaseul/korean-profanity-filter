package com.lily.spring_ai_vector.dto;

import com.lily.spring_ai_vector.enums.Category;

import java.util.List;

/**
 * 관리자 비속어 벡터 적재(Ingest) 요청 DTO
 */
public record RegisterRequest(
        List<EmbedItem> embedItems,
        List<Long> judgeLogIds
) {
    public record EmbedItem(
            String text,
            String category,
            Integer severity,
            String wordType
    ) {
        public EmbedItem {
            if (category == null || category.isBlank()) category = Category.PROFANITY.name();
            if (severity == null || severity < 1 || severity > 3) severity = 1;
            if (wordType == null || wordType.isBlank()) wordType = "SENTENCE";
        }
    }
}