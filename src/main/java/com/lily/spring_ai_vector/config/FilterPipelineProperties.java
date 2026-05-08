package com.lily.spring_ai_vector.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * filter-pipeline.yaml 에 정의된 필터 파이프라인 설정값 바인딩 클래스
 * - 각 필터별 세부 설정값을 구조화하여 캡슐화
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