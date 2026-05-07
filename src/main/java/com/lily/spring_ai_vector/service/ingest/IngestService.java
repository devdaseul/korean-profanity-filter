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

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * [관리자 전용] 벡터 DB 적재(Ingest) 서비스
 * 
 * 1. 데이터 수집: 직접 입력된 단어 + LLM이 찾아낸 미승인 로그
 * 2. 규격화: 수집된 텍스트를 Spring AI의 'Document' 객체로 변환
 * 3. 벡터화 및 저장: VectorStore를 통해 임베딩 모델 호출 후 pgvector에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private final VectorStore vectorStore;
    private final LlmJudgeLogRepository logRepository;

    @Transactional
    public RegisterResponse ingest(RegisterRequest request) {
        
        List<Document> documents = new ArrayList<>();
        Set<String> savedWords = new HashSet<>();
        
        // 1-A. 관리자가 API로 직접 입력한 단어들 (각각의 메타데이터 적용)
        if (request.embedItems() != null) {
            for (var item : request.embedItems()) {
                if (item.text() == null || item.text().isBlank()) continue;
                
                String deterministicId = UUID.nameUUIDFromBytes(item.text().getBytes(StandardCharsets.UTF_8)).toString();
                documents.add(new Document(
                        deterministicId,
                        item.text(),
                        Map.of(
                                "category", Category.fromString(item.category()).getLabel(), // 한글 카테고리로 저장
                                "severity", item.severity(),
                                "wordType", item.wordType(),
                                "source_type", "ADMIN_DIRECT"
                        )
                ));
                savedWords.add(item.text());
            }
        }

        // 1-B. LLM이 찾아냈던 '미검토 로그(judgeLogIds)'를 확인하고 승인 처리 후 추가
        if (request.judgeLogIds() != null && !request.judgeLogIds().isEmpty()) {
            List<LlmJudgeLog> logs = logRepository.findAllById(request.judgeLogIds());
            
            for (LlmJudgeLog logItem : logs) {
                if (Boolean.TRUE.equals(logItem.getIsTrained()) || logItem.getNormalizedText() == null) {
                    continue;
                }
                
                logItem.setIsTrained(true);
                logItem.setReviewedBy("ADMIN");
                
                // LlmJudgeLog의 enum 기반 Category에서 바로 한글 라벨을 가져옵니다.
                String catLabel = logItem.getCategory() != null ? logItem.getCategory().getLabel() : Category.PROFANITY.getLabel();
                int sev = logItem.getSeverity() != null ? logItem.getSeverity() : 1;
                String word = logItem.getNormalizedText();
                
                String deterministicId = UUID.nameUUIDFromBytes(word.getBytes(StandardCharsets.UTF_8)).toString();
                documents.add(new Document(
                        deterministicId,
                        word,
                        Map.of(
                                "category", catLabel, // 한글 카테고리로 저장
                                "severity", sev,
                                "wordType", "SENTENCE",
                                "source_type", "LLM_LOG_TRAINING"
                        )
                ));
                savedWords.add(word);
            }
        }

        if (documents.isEmpty()) {
            return new RegisterResponse(0, "등록할 데이터가 없습니다.", "EMPTY", Collections.emptyList());
        }

        log.info("[Ingest] 임베딩 대기 문서 수: {}건", documents.size());
        vectorStore.add(documents);
        
        log.info("[Ingest] 벡터 DB 저장 완료! (Upsert 동작)");
        return new RegisterResponse(
                documents.size(), 
                "성공적으로 반영되었습니다.", 
                "SUCCESS", 
                new ArrayList<>(savedWords)
        );
    }
}