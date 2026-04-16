package com.lily.spring_ai_vector.dto;

/**
 * [API 2 / API 3] 공통 사용자 입력 요청 DTO
 *
 * - text: 필터링 또는 순화할 사용자 입력 문장
 */
public record FilterRequest(String text) {}
