package com.lily.spring_ai_vector.dto;

/**
 * 파이프라인 필터링 응답 DTO
 *
 * stageResults 상태값 규칙:
 *  - "통과"       : 비속어 아님 → 다음 단계 진행
 *  - "비속어 탐지" : 비속어 판정 → 파이프라인 즉시 종료
 *  - "스킵"       : 이전 단계 탐지로 실행되지 않음
 *  - (내용 문자열) : RAG/LLM 단계는 실제 결과 내용을 담음
 */
public record PipelineResponse(
        String originalText,
        String normalizedText,
        boolean isProfanity,
        String detectedBy,
        long pipelineMs,
        StageResults stageResults
) {

    private static final String MSG_PROFANITY = "비속어가 포함된 문장입니다.";
    private static final String MSG_SAFE      = "정상적인 문장입니다.";

    /** isProfanity 에서 파생되는 메시지 */
    public String resultMessage() {
        return isProfanity ? MSG_PROFANITY : MSG_SAFE;
    }

    public record StageResults(
            String step1_1_Normalize,
            String step1_2_Regex,
            String step1_3_Blacklist,
            String step1_4_Fuzzy,
            String step2_Rag,
            Object step3_Llm
    ) {}

    /** L1: Regex / Blacklist / Fuzzy 탐지 */
    public static PipelineResponse ofFastExit(
            String original, String normalized, String detectedBy, long ms, StageResults stages) {
        return new PipelineResponse(original, normalized, true, detectedBy, ms, stages);
    }

    /** L2: RAG 유사도 검색 탐지 */
    public static PipelineResponse ofRag(
            String original, String normalized, long ms, StageResults stages) {
        return new PipelineResponse(original, normalized, true, "L2-RAG", ms, stages);
    }

    /** L3: LLM 최종 판정 */
    public static PipelineResponse ofLlm(
            String original, String normalized, boolean isProfanity, long ms, StageResults stages) {
        return new PipelineResponse(original, normalized, isProfanity, "L3-LLM", ms, stages);
    }
}

