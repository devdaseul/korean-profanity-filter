package com.lily.spring_ai_vector.dto;

/** 비속어 등록 결과 응답 DTO */
public record RegisterResponse(
        int savedCount,
        String message,
        String sourceType
) {}
