package com.lily.spring_ai_vector.service.ingest;

import com.lily.spring_ai_vector.dto.RegisterRequest;
import com.lily.spring_ai_vector.dto.RegisterResponse;
import com.lily.spring_ai_vector.entity.LlmJudgeLog;
import com.lily.spring_ai_vector.enums.Category;
import com.lily.spring_ai_vector.repository.LlmJudgeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 벡터 DB 임베딩 저장 서비스
 *
 * 입력 경로 2가지:
 *  1. texts  : API로 직접 전달된 문자열 목록
 *  2. logIds : 관리자가 승인한 LLM 판정 로그 ID → normalizedText 를 임베딩
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private final VectorStore vectorStore;
    private final LlmJudgeLogRepository logRepository;

    @Transactional
    public RegisterResponse ingest(RegisterRequest request) {
        log.info("[Ingest] 처리 대상 | 입력값: {}건, 로그번호: {}건",
                CollectionUtils.isEmpty(request.texts())  ? 0 : request.texts().size(),
                CollectionUtils.isEmpty(request.logIds()) ? 0 : request.logIds().size());

        Set<String> combinedWords = new HashSet<>();

        // 1. 직접 입력 단어(texts) 처리
        if (!CollectionUtils.isEmpty(request.texts())) {
            request.texts().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(combinedWords::add);
        }

        // 2. 관리자 승인 로그(logIds) 처리
        if (!CollectionUtils.isEmpty(request.logIds())) {
            List<LlmJudgeLog> logs = logRepository.findAllById(request.logIds());
            for (LlmJudgeLog refinedLog : logs) {
                if (Boolean.TRUE.equals(refinedLog.getIsTrained())) continue;  // 이미 학습된 건 스킵
                if (!StringUtils.hasText(refinedLog.getNormalizedText())) continue;

                combinedWords.add(refinedLog.getNormalizedText());
                refinedLog.setIsTrained(true);
                refinedLog.setReviewedBy("ADMIN_UI");
                refinedLog.setCategory(Category.fromString(request.category()));
            }
        }

        // 3. 저장 대상 없음 체크
        if (combinedWords.isEmpty()) {
            log.warn("[Ingest] 저장할 데이터가 없습니다.");
            return new RegisterResponse(0, "저장할 데이터가 없습니다.", "NONE");
        }

        // 4. Document 변환 후 벡터 DB 저장
        String category = request.category();
        List<Document> docsToEmbed = combinedWords.stream()
                .map(word -> new Document(word, Map.of("category", category, "source", "ADMIN_DIRECT")))
                .toList();

        log.info("[Ingest] 벡터 DB 저장 진행 | {}건", docsToEmbed.size());
        
        vectorStore.add(docsToEmbed);
        log.info("[Ingest] 벡터 임베딩 저장 완료");

        return new RegisterResponse(docsToEmbed.size(), "성공적으로 반영되었습니다.", "SUCCESS");
    }
    
}