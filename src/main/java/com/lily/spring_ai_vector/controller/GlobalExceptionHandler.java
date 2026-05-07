package com.lily.spring_ai_vector.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 전역 예외 처리기
 * 
 * 에러 발생 시 클라이언트에게 스택 트레이스나 시스템 내부 정보가 노출되는 현상(정보 유출 안티패턴)을 방지합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("[GlobalException] 서버 오류 발생: {}", e.getMessage(), e);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "status", 500,
                        "error", "Internal Server Error",
                        "message", "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                ));
    }
}
