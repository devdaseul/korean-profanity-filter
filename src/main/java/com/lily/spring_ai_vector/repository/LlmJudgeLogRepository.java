package com.lily.spring_ai_vector.repository;

import com.lily.spring_ai_vector.entity.LlmJudgeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LlmJudgeLogRepository extends JpaRepository<LlmJudgeLog, Long> {

    /** 벡터 DB 미편입 로그 조회 (관리자 검토 대기 목록) */
    List<LlmJudgeLog> findByIsTrainedFalse();
}
