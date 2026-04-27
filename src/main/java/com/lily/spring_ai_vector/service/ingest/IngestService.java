package com.lily.spring_ai_vector.service.ingest;

import com.lily.spring_ai_vector.dto.RegisterRequest;
import com.lily.spring_ai_vector.dto.RegisterResponse;
import com.lily.spring_ai_vector.entity.LlmJudgeLog;
import com.lily.spring_ai_vector.repository.LlmJudgeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 벡터 DB 임베딩 저장 서비스
 *
 * ▶ 입력 경로 2가지:
 *   1. texts  : API로 직접 전달된 문자열 목록
 *   2. logIds : 관리자가 승인한 LLM 판정 로그 ID 목록 → normalizedText 를 임베딩 대상으로 사용
 *
 * ▶ 지속 학습 루프:
 *   LLM 판정 로그 → 관리자 검토(judge-logs API) → logIds 로 ingest 호출 → 벡터 DB 반영
 *   → 다음 L2(RAG) 검색부터 즉시 적용됨
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private final VectorStore vectorStore;
    private final LlmJudgeLogRepository logRepository;

    @Transactional
    public RegisterResponse ingest(RegisterRequest request) {
        log.info("[Ingest] ── 시작 ─────────────────────────────");
        log.info("[Ingest] 요청 | texts={}건 logIds={}건 category={}",
                request.texts()  != null ? request.texts().size()  : 0,
                request.logIds() != null ? request.logIds().size() : 0,
                request.category());

        Set<String> combinedWords = new HashSet<>();

        // 1. 직접 입력 단어(texts) 처리
        if (request.texts() != null && !request.texts().isEmpty()) {
            request.texts().stream()
                    .filter(t -> t != null && !t.isBlank())
                    .forEach(t -> combinedWords.add(t.trim()));
            log.info("[Ingest] texts 경로 | {}건 추가됨", combinedWords.size());
        }

        // 2. 관리자 승인 로그(logIds) 처리
        if (request.logIds() != null && !request.logIds().isEmpty()) {
            List<LlmJudgeLog> logs = logRepository.findAllById(request.logIds());
            int skipped = 0;
            for (LlmJudgeLog refinedLog : logs) {
                if (refinedLog.getNormalizedText() != null) {
                    // 이미 학습된 데이터는 중복 저장 방지
                    if (Boolean.TRUE.equals(refinedLog.getIsTrained())) {
                        log.info("[Ingest] logId={} 스킵 (이미 학습 완료)", refinedLog.getId());
                        skipped++;
                        continue;
                    }
                    combinedWords.add(refinedLog.getNormalizedText());
                    refinedLog.setIsTrained(true);
                    refinedLog.setReviewedBy("ADMIN_UI");
                    if (request.category() != null) refinedLog.setCategory(request.category());
                    log.info("[Ingest] logId={} 승인 | '{}' → 임베딩 대상 추가",
                            refinedLog.getId(), refinedLog.getNormalizedText());
                }
            }
            log.info("[Ingest] logIds 경로 | 처리={}건 스킵={}건",
                    logs.size() - skipped, skipped);
        }

        // 3. 저장 대상 없음 체크
        if (combinedWords.isEmpty()) {
            log.warn("[Ingest] 저장할 데이터가 없습니다. 요청 내용을 확인하세요.");
            return new RegisterResponse(0, "저장할 데이터가 없습니다.", "NONE");
        }

        // 4. Document 변환 후 벡터 DB 저장 (bge-m3 임베딩 자동 실행)
        List<Document> docsToEmbed = combinedWords.stream()
                .map(word -> new Document(word, Map.of(
                        "category", request.category() != null ? request.category() : "PROFANITY",
                        "source", "ADMIN_DIRECT"
                )))
                .toList();

        log.info("[Ingest] bge-m3 임베딩 + pgvector 저장 시작 | {}건", docsToEmbed.size());
        vectorStore.add(docsToEmbed);
        log.info("[Ingest] ── 완료 | {}건 임베딩 저장 성공 ─────────────", docsToEmbed.size());

        return new RegisterResponse(docsToEmbed.size(), "성공적으로 반영되었습니다.", "SUCCESS");
    }
}