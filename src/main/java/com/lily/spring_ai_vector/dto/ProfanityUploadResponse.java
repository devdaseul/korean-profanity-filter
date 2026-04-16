package com.lily.spring_ai_vector.dto;

/**
 * [API 1] 업로드 결과 응답 DTO
 *
 * - savedCount: 실제로 vectorDB에 저장된 문서 수
 * - message: 성공/실패 메시지
 * - sourceType: 입력 경로 (API / TXT / MD / JSON)
 */
public record ProfanityUploadResponse(
        int savedCount,
        String message,
        String sourceType
) {}
