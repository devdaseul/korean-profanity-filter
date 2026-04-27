package com.lily.spring_ai_vector.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * filter-pipeline.yaml 에 정의된 필터 파이프라인 설정값 바인딩
 *
 * ▶ normalize : 전처리 규칙 (시각적 치환, Qwerty 변환, 반복 글자 압축)
 * ▶ regex     : L1 정규식 패턴
 * ▶ fuzzy     : Jaccard N-Gram 유사도 임계값 (0.0~1.0)
 * ▶ rag       : L2 벡터 검색 유사도 임계값 및 topK
 */
@Validated
@ConfigurationProperties(prefix = "filter.pipeline")
public record FilterPipelineProperties(
    @Valid @NotNull Normalize normalize,
    @Valid @NotNull Regex regex,
    @Valid @NotNull Fuzzy fuzzy,
    @Valid @NotNull Rag rag
) {
    public record Normalize(
        @Min(1) int maxRepeat,
        Map<String, String> visualReplacements,
        Qwerty qwerty
    ) {}

    public record Qwerty(
        @NotBlank String eng,
        @NotBlank String kor
    ) {}

    public record Regex(
        @NotBlank String pattern
    ) {}

    public record Fuzzy(
        @DecimalMin("0.0") @DecimalMax("1.0") double threshold
    ) {}

    public record Rag(
        @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold,
        @Min(1) int topK
    ) {}
}