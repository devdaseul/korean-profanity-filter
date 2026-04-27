package com.lily.spring_ai_vector.dto;

/**
 * [API 4] 파이프라인 필터링 응답 DTO
 *
 * ▶ StageResults 상태값 규칙 (모든 step 공통):
 *   "⏭ 건너뜀"      → 이전 단계에서 이미 탐지되어 이 단계는 실행하지 않음
 *   "통과"        → 이 단계에서 비속어 아님으로 판단, 다음 단계로 진행
 *   "비속어 탐지" → 이 단계에서 비속어로 판정, 파이프라인 즉시 종료
 *   (내용 문자열)    → RAG/LLM 단계는 실제 결과 내용을 담음
 *
 * ▶ stageResults 설계 의도:
 *   - 파이프라인의 각 단계(Step 1~6)가 어떤 결과를 냈는지 한 번의 응답으로 확인
 *   - null = 해당 단계에 도달하기 전에 이미 이전 단계에서 탐지되어 실행되지 않음
 *   - 실습/교육 시 Postman 한 번으로 전체 흐름을 시각적으로 확인 가능
 *
 * ▶ 응답 예시 (REGEX 단계에서 탐지된 경우):
 * {
 *   "originalText":   "씨1발 진짜",
 *   "normalizedText": "씨1발 진짜",
 *   "isProfanity":    true,
 *   "resultMessage":  "비속어가 포함된 문장입니다.",
 *   "detectedBy":     "Step2-Regex",
 *   "pipelineMs":     8,
 *   "stageResults": {
 *     "step1Normalize":  "씨실발 진짜",
 *     "step2Regex":      "🚨 비속어 탐지",
 *     "step3Blacklist":  "⏭ 건너뜀",
 *     "step4Fuzzy":      "⏭ 건너뜀",
 *     "step5Rag":        "⏭ 건너뜀",
 *     "step6Llm":        "⏭ 건너뜀"
 *   }
 * }
 */
public record PipelineResponse(
        String originalText,
        String normalizedText,
        boolean isProfanity,
        String resultMessage,
        String detectedBy,
        long pipelineMs,
        StageResults stageResults
) {

    /** 비속어 탐지 시 사용하는 표준 메시지 */
    public static final String MSG_PROFANITY = "비속어가 포함된 문장입니다.";
    /** 정상 판정 시 사용하는 표준 메시지 */
    public static final String MSG_SAFE = "정상적인 문장입니다.";

    // 단계별 실행 결과를 담는 중첩 레코드

    /**
     * 각 Step 의 실행 결과
     *
     * ▶상태값 규칙 (모든 step 공통):
     *  - "통과"       : 이 단계 비속어 아님 → 다음 단계 진행
     *  - "비속어 탐지" : 이 단계에서 비속어 판정 → 파이프라인 종료
     *  - "스킵"     : 이전 단계 탐지로 이 단계 실행 안 함
     *  - (내용 문자열)   : RAG/LLM 단계는 실제 결과 내용을 담음
     */
    public record StageResults(
            String step1Normalize,   // 전처리 결과 텍스트
            String step2Regex,       // "통과" | "비속어 탐지" | "스킵"
            String step3Blacklist,   // "통과" | "비속어 탐지" | "스킵"
            String step4Fuzzy,       // "통과" | "비속어 탐지" | "스킵"
            String step5Rag,         // "유사 문서 없음" | "N건 탐지: ..." | "스킵"
            String step6Llm          // LLM 원문 응답 | "스킵"
    ) {}

    /** Fast-Exit(Regex/Blacklist/Fuzzy) 단계에서 탐지된 경우 */
    public static PipelineResponse ofFastExit(
            String original, String normalized, String detectedBy, long ms,
            StageResults stages) {
        return new PipelineResponse(
                original, normalized, true, MSG_PROFANITY, detectedBy, ms, stages);
    }

    /** RAG 단계에서 비속어로 판정된 경우 */
    public static PipelineResponse ofRag(
            String original, String normalized, long ms, StageResults stages) {
        return new PipelineResponse(
                original, normalized, true, MSG_PROFANITY, "Step5-RAG", ms, stages);
    }

    /** SAFE 데이터를 통해 Fast-Exit한 경우 */
    public static PipelineResponse ofSafeFastExit(
            String original, String normalized, String detectedBy, long ms, StageResults stages) {
        return new PipelineResponse(
                original, normalized, false, MSG_SAFE, detectedBy, ms, stages);
    }

    /** LLM 단계 판정 결과 */
    public static PipelineResponse ofLlm(
            String original, String normalized, boolean isProfanity, long ms, StageResults stages) {
        return new PipelineResponse(
                original, normalized, isProfanity,
                isProfanity ? MSG_PROFANITY : MSG_SAFE,
                "Step6-LLM", ms, stages);
    }
}
