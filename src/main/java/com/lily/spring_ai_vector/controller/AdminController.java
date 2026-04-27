package com.lily.spring_ai_vector.controller;

import com.lily.spring_ai_vector.dto.RegisterRequest;
import com.lily.spring_ai_vector.dto.RegisterResponse;
import com.lily.spring_ai_vector.entity.LlmJudgeLog;
import com.lily.spring_ai_vector.repository.LlmJudgeLogRepository;
import com.lily.spring_ai_vector.service.ingest.IngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자용 컨트롤러
 *
 *   1. GET  /api/admin/judge-logs L3가 판정한 로그 중 미등록 목록 확인
 *   2. POST /api/admin/ingest     선택한 로그를 벡터 DB에 임베딩 저장
 *   저장된 데이터는 다음 L2(RAG) 검색에 즉시 반영됨
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final IngestService ingestService;
    private final LlmJudgeLogRepository judgeLogRepository;

    /**
     * 벡터 DB 등록 API
     */
    @PostMapping("/ingest")
    public ResponseEntity<RegisterResponse> ingest(
            @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(ingestService.ingest(request));
    }

    /**
     * 관리자 검토용 로그 조회 API
     * - L3에서 판정된 로그 중 아직 벡터 DB에 편입되지 않은 항목들을 반환
     * - 관리자는 이 목록을 보고 벡터 DB에 등록할 로그를 선택하여 등록 API에 전달할 수 있음
     */
    @GetMapping("/judge-logs")
    public ResponseEntity<List<LlmJudgeLog>> getJudgeLogs() {
        return ResponseEntity.ok(judgeLogRepository.findByIsTrainedFalse());
    }
}
