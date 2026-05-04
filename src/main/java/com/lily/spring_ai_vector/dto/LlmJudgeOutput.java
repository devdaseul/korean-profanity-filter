package com.lily.spring_ai_vector.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.lily.spring_ai_vector.enums.Category;
import com.lily.spring_ai_vector.enums.JudgeResult;

/**
 * LLM 판정 응답 DTO (Spring AI Structured Output)
 *
 * ChatClient.call().entity(LlmJudgeOutput.class) 호출 시
 * Spring AI가 JSON 스키마를 자동으로 시스템 프롬프트에 삽입하고
 * LLM 응답을 이 record로 역직렬화한다.
 */
public record LlmJudgeOutput(
        @JsonPropertyDescription("판정 결과. 비속어면 PROFANITY, 정상 문장이면 SAFE")
        JudgeResult result,
        @JsonPropertyDescription("판단 근거를 1~2문장으로 간결하게 설명")
        String reason,
        @JsonPropertyDescription("비속어 유형. PROFANITY, ABUSE, SEXUAL, HATE, UNCATEGORIZED 중 하나")
        Category category,
        @JsonPropertyDescription("위험도. 1(경미), 2(보통), 3(심각) 중 하나")
        int severity
) {}
