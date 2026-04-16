package com.lily.spring_ai_vector.dto;

import java.util.List;

/**
 * [API 2] RAG 마스킹 필터 결과 응답 DTO
 *
 * 필드 설계 근거:
 *  - originalText: 원문 보존 → 클라이언트가 변환 전후를 비교 가능
 *  - maskedText: 비속어를 *** 로 마스킹한 결과
 *  - detectedWords: 감지된 비속어 목록 → 관리자 모니터링·로그에 활용
 *  - hasProfanity: 비속어 포함 여부 bool → 클라이언트 측 빠른 분기 처리
 *  - ragSearchMs: RAG 검색 소요 시간(ms) → 성능 모니터링용
 */
public record MaskFilterResponse(
        String originalText,
        String maskedText,
        List<String> detectedWords,
        boolean hasProfanity,
        long ragSearchMs
) {}
