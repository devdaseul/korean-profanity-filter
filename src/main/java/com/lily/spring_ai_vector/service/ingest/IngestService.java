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

import java.util.*;
import java.util.stream.Collectors;

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
        
        Set<String> wordsToEmbed = new HashSet<>();
        
        // 1-A. 관리자가 API로 직접 입력한 단어들 추가
        if (request.texts() != null) {
            wordsToEmbed.addAll(request.texts());
        }

        // 1-B. LLM이 찾아냈던 '미검토 로그(logIds)'를 확인하고 승인 처리 후 추가
        if (request.logIds() != null && !request.logIds().isEmpty()) {
            List<LlmJudgeLog> logs = logRepository.findAllById(request.logIds());
            
            for (LlmJudgeLog logItem : logs) {
                if (Boolean.TRUE.equals(logItem.getIsTrained()) || logItem.getNormalizedText() == null) {
                    continue;
                }
                
                wordsToEmbed.add(logItem.getNormalizedText());
                logItem.setIsTrained(true);
                logItem.setReviewedBy("ADMIN");
                logItem.setCategory(Category.fromString(request.category()));
            }
        }

        log.info("[Ingest] 임베딩 대기 문서 수: {}건", wordsToEmbed.size());

        List<Document> documents = wordsToEmbed.stream()
                .filter(word -> !word.isBlank())
                .map(word -> new Document(
                        word, 
                        Map.of(
                            "category", request.category(),
                            "source", "ADMIN_DIRECT"
                        )
                ))
                .collect(Collectors.toList());

        vectorStore.add(documents);
        
        log.info("[Ingest] 벡터 DB 저장 완료!");
        return new RegisterResponse(
                documents.size(), 
                "성공적으로 반영되었습니다.", 
                "SUCCESS", 
                new ArrayList<>(wordsToEmbed)
        );
    }
}