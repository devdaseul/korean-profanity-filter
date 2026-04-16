package com.lily.spring_ai_vector.controller;

import com.lily.spring_ai_vector.dto.*;
import com.lily.spring_ai_vector.service.ProfanityFilterService;
import com.lily.spring_ai_vector.service.ProfanityRefineService;
import com.lily.spring_ai_vector.service.ProfanityUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 비속어 필터링 API 컨트롤러
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  API 1  POST /api/admin/profanity/words    단어 직접 입력    │
 * │  API 1  POST /api/admin/profanity/file     파일 업로드       │
 * │  API 2  POST /api/filter/mask              RAG 마스킹        │
 * │  API 3  POST /api/filter/refine            LLM 순화          │
 * └─────────────────────────────────────────────────────────────┘
 *
 * 왜 admin 경로를 별도 분리?
 *  - 관리자 전용 API와 사용자 API를 URL 경로로 구분 → 인증 미들웨어 적용 편의
 *  - 향후 Spring Security 에서 /api/admin/** 에만 ROLE_ADMIN 을 걸 수 있도록 설계
 */
@RestController
@RequiredArgsConstructor
public class FilterController {

    private final ProfanityUploadService uploadService;
    private final ProfanityFilterService filterService;
    private final ProfanityRefineService refineService;

    // ──────────────────────────────────────────────────────────
    //  [API 1-A] 관리자: 단어/문장 직접 입력 → VectorDB 저장
    // ──────────────────────────────────────────────────────────

    /**
     * POST /api/admin/profanity/words
     *
     * Postman Body 예시:
     * {
     *   "words": ["개새끼", "시발", "병신아"],
     *   "category": "PROFANITY",
     *   "severity": 2,
     *   "wordType": "WORD"
     * }
     *
     * 왜 RequestBody 방식?
     *  - 소량 단어를 JSON 으로 즉시 등록 → 파일 준비 없이 빠른 업데이트 가능
     *  - category/severity 를 단어별로 세밀하게 지정 가능
     */
    @PostMapping(
            value = "/api/admin/profanity/words",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ProfanityUploadResponse> uploadWords(
            @RequestBody ProfanityUploadRequest request
    ) {
        ProfanityUploadResponse response = uploadService.uploadFromRequest(request);
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────────────────
    //  [API 1-B] 관리자: 파일 업로드 → VectorDB 저장
    // ──────────────────────────────────────────────────────────

    /**
     * POST /api/admin/profanity/file
     *
     * Postman 설정:
     *  - Body → form-data
     *  - Key: "files"    Value: [파일 선택] (.txt / .md / .json) → 여러 개 추가 가능!
     *  - Key: "category" Value: PROFANITY (선택, 기본값=파일명 추론)
     *  - Key: "severity" Value: 2         (선택, 기본값=1)
     *
     * 왜 Multipart?
     *  - 수천~수만 줄의 대용량 비속어 사전을 한 번에 업로드
     *  - 파일 형식(txt/md/json)에 따라 자동 파서 분기
     *  - API 기반 업로드로 서버 재시작 없이 비속어 사전 갱신 가능
     *  - 다중 파일 업로드 지원 → 여러 사전 파일을 한 번에 등록 가능
     */
    @PostMapping(
            value = "/api/admin/profanity/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ProfanityUploadResponse> uploadFile(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "severity", defaultValue = "1") int severity
    ) throws IOException {
        ProfanityUploadResponse response = uploadService.uploadFromFiles(files, category, severity);
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────────────────
    //  [API 2] 사용자: 입력 문장 → RAG 마스킹 필터링
    // ──────────────────────────────────────────────────────────

    /**
     * POST /api/filter/mask
     *
     * Postman Body 예시:
     * { "text": "야 이 개새끼야 어디가는 거야" }
     *
     * 응답 예시:
     * {
     *   "originalText": "야 이 개새끼야 어디가는 거야",
     *   "maskedText":   "야 이 *****야 어디가는 거야",
     *   "detectedWords": ["개새끼야"],
     *   "hasProfanity": true,
     *   "ragSearchMs":  42
     * }
     *
     * 언제 사용?
     *  - 실시간 채팅, 댓글 등 빠른 응답이 필요한 경우
     *  - LLM 추론 비용을 아끼고 싶을 때
     *  - 비속어를 마스킹만 하고 문장 구조를 바꾸지 않아야 할 때
     */
    @PostMapping(
            value = "/api/filter/mask",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<MaskFilterResponse> mask(@RequestBody FilterRequest request) {
        MaskFilterResponse response = filterService.mask(request.text());
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────────────────
    //  [API 3] 사용자: 입력 문장 → RAG + LLM 순화
    // ──────────────────────────────────────────────────────────

    /**
     * POST /api/filter/refine
     *
     * Postman Body 예시:
     * { "text": "시1발 이 영화 개노잼이네" }
     *
     * 응답 예시:
     * {
     *   "originalText":  "시1발 이 영화 개노잼이네",
     *   "refinedText":   "이 영화는 정말 재미없네요",
     *   "ragSearchMs":   35,
     *   "llmInferenceMs": 1200,
     *   "totalMs":       1235
     * }
     *
     * 언제 사용?
     *  - 게시글, 리뷰 등 고품질 순화가 필요한 경우
     *  - API 2 로 잡히지 않은 철자 변형·신조어를 LLM 이 최종 처리
     *  - 비속어를 단순 마스킹이 아닌 자연스러운 대체 표현으로 변환
     */
    @PostMapping(
            value = "/api/filter/refine",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<RefineResponse> refine(@RequestBody FilterRequest request) {
        RefineResponse response = refineService.refine(request.text());
        return ResponseEntity.ok(response);
    }
}
