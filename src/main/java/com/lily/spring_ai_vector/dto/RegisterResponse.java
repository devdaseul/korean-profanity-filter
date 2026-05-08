package com.lily.spring_ai_vector.dto;

import java.util.List;

/** 비속어 등록 결과 응답 DTO */
public record RegisterResponse(
        int savedCount,
        String message,
        String status,
        List<String> savedWords
) {}
