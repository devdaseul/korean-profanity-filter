package com.lily.spring_ai_vector.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

import org.hibernate.annotations.UpdateTimestamp;

/**
 * LLM 판정 로그 엔티티
 *
 * ▶ 저장 시점:
 *   - 전수 필터(Normalize → Regex → Blacklist → Fuzzy → RAG)를 모두 통과한 케이스
 *   - 즉, LLM 이 최종 판단한 "모호한 문장들"의 기록
 *
 * ▶ is_trained (★ 핵심 콜럼):
 *   - FALSE: 아직 벡터 DB에 편입되지 않은 상태 (검토 대기)
 *   - TRUE:  관리자가 검토 후 벡터 DB에 임베딩 저장 완료
 *   - 이 콜럼이 "지속 학습(Continuous Learning)" 루프의 출발점
 *
 * ▶ llm_result:
 *   - "PROFANITY": LLM 이 비속어로 판단 (RAG 가 못 잡은 신규 패턴 후보)
 *   - "SAFE": LLM 이 정상으로 판단 (False Positive 방지용 화이트리스트 후보)
 */
@Entity
@Table(name = "llm_judge_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmJudgeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 원문 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalText;

    /** 전처리(Normalize) 후 텍스트 */
    @Column(columnDefinition = "TEXT")
    private String normalizedText;

    /** LLM 판단 결과: PROFANITY | SAFE */
    @Column(nullable = false, length = 20)
    private String llmResult;

    /** LLM 확신도 (0.0~1.0). 낮을수록 모호한 케이스 */
    private Double confidence;

    /** 벡터 DB 재학습 편입 여부 (관리자가 TRUE 로 변경하면 임베딩 대상) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isTrained = false;

    /** 검토한 관리자 이름 또는 ID */
    @Column(length = 100)
    private String reviewedBy;

    /** LLM이 판단한 사유 (REASON 파싱 결과) */
    private String llmReason;
    /** 비속어 유형: PROFANITY / ABUSE / SEXUAL / HATE 등 */
    private String category;
    /** 위험도 (1: 경미 ~ 3: 심각) */
    private Integer severity;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
