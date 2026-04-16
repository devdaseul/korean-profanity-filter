package com.lily.spring_ai_vector.dto;

import java.util.List;

/**
 * [API 1] 관리자 비속어 업로드 요청 DTO
 *
 * 사용 시나리오:
 *  - 단일/복수 단어·문장을 JSON Body로 직접 전송
 *  - 파일 업로드(MultipartFile)와 구분되는 텍스트 직접 입력 경로
 *
 * 필드 설계 근거:
 *  - words: 여러 단어/문장을 한 번에 배치 처리하기 위해 List 사용
 *  - category: PROFANITY / HATE_SPEECH / SEXUAL 등 벡터 필터링 시 pre-filter 에 사용
 *  - severity: 1(경미)~3(심각), 마스킹 강도 및 순화 방식 분기에 활용
 *  - wordType: WORD / PHRASE / REGEX → 향후 정규식 패턴 지원 확장 대비
 */
public record ProfanityUploadRequest(
        List<String> words,

        /**
         * 카테고리: PROFANITY(일반 비속어), HATE_SPEECH(혐오표현), SEXUAL(성적 표현)
         * 기본값은 서비스 레이어에서 "PROFANITY"로 처리
         */
        String category,

        /**
         * 심각도: 1=경미(은어·속어), 2=일반 비속어, 3=혐오·성적 표현
         * 기본값 1
         */
        Integer severity,

        /**
         * 단어 유형: WORD(단일 단어), PHRASE(문장 형태), REGEX(정규식)
         * 기본값 "WORD"
         */
        String wordType
) {
    /** 기본값 처리용 compact constructor */
    public ProfanityUploadRequest {
        if (category == null || category.isBlank()) category = "PROFANITY";
        if (severity == null || severity < 1 || severity > 3) severity = 1;
        if (wordType == null || wordType.isBlank()) wordType = "WORD";
    }
}
