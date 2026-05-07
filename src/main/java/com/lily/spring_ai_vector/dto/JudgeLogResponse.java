package com.lily.spring_ai_vector.dto;

import com.lily.spring_ai_vector.entity.LlmJudgeLog;

import java.time.OffsetDateTime;

/**
 * DB 엔티티 외부 노출 방지용 DTO
 */
public record JudgeLogResponse(
        Long id,
        String originalText,
        String normalizedText,
        String llmResult,
        String llmReason,
        String category,
        Integer severity,
        OffsetDateTime createdAt
) {
    public static JudgeLogResponse from(LlmJudgeLog log) {
        return new JudgeLogResponse(
                log.getId(),
                log.getOriginalText(),
                log.getNormalizedText(),
                log.getLlmResult() != null ? log.getLlmResult().name() : null,
                log.getLlmReason(),
                log.getCategory() != null ? log.getCategory().getLabel() : null,
                log.getSeverity(),
                log.getCreatedAt()
        );
    }
}
