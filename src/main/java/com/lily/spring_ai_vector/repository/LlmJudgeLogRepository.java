package com.lily.spring_ai_vector.repository;

import com.lily.spring_ai_vector.entity.LlmJudgeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * LLM 판정 로그 리포지토리
 *
 * ▶ findByIsTrainedFalse():
 *   - 아직 벡터 DB에 편입되지 않은 로그 조회
 *   - 관리자 검토 화면 또는 배치 작업에서 사용
 *
 * ▶ 향후 확장 예시:
 *   - findByLlmResultAndIsTrainedFalse("PROFANITY") → 비속어 신규 패턴만 추출
 */
public interface LlmJudgeLogRepository extends JpaRepository<LlmJudgeLog, Long> {

    /** 벡터 DB 미편입 로그 전체 조회 (관리자 검토 대기 목록) */
    List<LlmJudgeLog> findByIsTrainedFalse();
}
