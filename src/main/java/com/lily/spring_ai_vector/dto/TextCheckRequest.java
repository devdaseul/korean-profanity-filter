package com.lily.spring_ai_vector.dto;

/**
 * 사용자 텍스트 검사 요청 DTO
 *
 * - text: 필터링할 사용자 입력 문장
 */
public record TextCheckRequest(String text) {}
