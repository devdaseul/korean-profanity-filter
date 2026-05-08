package com.lily.spring_ai_vector.entity;

import com.lily.spring_ai_vector.enums.Category;
import com.lily.spring_ai_vector.enums.JudgeResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

import org.hibernate.annotations.UpdateTimestamp;

/**
 * LLM 판정 로그 엔티티 (llm_judge_logs 테이블)
 *
 * isTrained: false = 관리자 검토 대기, true = 벡터 DB 학습 완료
 */
@Entity
@Table(name = "llm_judge_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmJudgeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Column(columnDefinition = "TEXT")
    private String normalizedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JudgeResult llmResult;

    private Double confidence;

    /** false = 관리자 검토 대기, true = 벡터 DB 저장 완료 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isTrained = false;

    @Column(length = 100)
    private String reviewedBy;

    private String llmReason;

    @Enumerated(EnumType.STRING)
    private Category category;

    private Integer severity;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
