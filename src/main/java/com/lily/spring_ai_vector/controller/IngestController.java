package com.lily.spring_ai_vector.controller;

import com.lily.spring_ai_vector.dto.JudgeLogResponse;
import com.lily.spring_ai_vector.dto.RegisterRequest;
import com.lily.spring_ai_vector.dto.RegisterResponse;
import com.lily.spring_ai_vector.repository.LlmJudgeLogRepository;
import com.lily.spring_ai_vector.service.ingest.IngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자용 API 컨트롤러
 * 미검토 LLM 판정 로그 조회 및 벡터 DB 학습 연동
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class IngestController {

    private final IngestService ingestService;
    private final LlmJudgeLogRepository judgeLogRepository;

    @PostMapping("/ingest")
    public ResponseEntity<RegisterResponse> ingest(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ingestService.ingest(request));
    }

    @GetMapping("/judge-logs")
    public ResponseEntity<List<JudgeLogResponse>> getJudgeLogs() {
        List<JudgeLogResponse> logs = judgeLogRepository.findByIsTrainedFalse()
                .stream()
                .map(JudgeLogResponse::from)
                .toList();
        return ResponseEntity.ok(logs);
    }
}
