package com.lily.spring_ai_vector.service.filter;

import com.lily.spring_ai_vector.config.FilterPipelineProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [L2] RAG 기반 유사도 필터 서비스
 *
 * 벡터 DB에서 정규화된 입력과 유사한 문서를 검색하여 비속어 여부를 판단
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagFilterService {

    private final VectorStore vectorStore;
    private final LocalFilterService localFilterService;
    private final FilterPipelineProperties props;

    public record Result(
            String originalText,
            String normalized,
            boolean isProfanity,
            List<String> matchedDocuments
    ) {}

    public Result check(String input) {
        log.info("[L2-RAG] ── 시작 ────────────────────────────");
        String normalized = localFilterService.normalize(input);
        log.info("[L2-RAG] 정규화 결과: '{}'", normalized);
        log.info("[L2-RAG] 벡터 검색 조건 | topK={} similarityThreshold={}",
                props.rag().topK(), props.rag().similarityThreshold());

        List<Document> docs = vectorStore.similaritySearch(
        SearchRequest.builder()
                .query(normalized)
                .topK(props.rag().topK())
                .similarityThreshold(props.rag().similarityThreshold())
                .build()
        );

        boolean isProfanity = !docs.isEmpty();
        List<String> matched = docs.stream().map(Document::getText).toList();

        if (isProfanity) {
            log.info("[L2-RAG] ★ 탐지 | 유사 문서 {}건:", matched.size());
            matched.forEach(doc -> log.info("[L2-RAG]   - '{}'", doc));
        } else {
            log.info("[L2-RAG] 통과 | 유사 문서 없음 (threshold={} 미만)",
                    props.rag().similarityThreshold());
        }
        log.info("[L2-RAG] ── 완료 | isProfanity={} ─────────────────", isProfanity);
        return new Result(input, normalized, isProfanity, matched);
    }

    public Map<String, Object> checkAsMap(String input) {
        Result r = check(input);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("originalText",      r.originalText());
        response.put("isProfanity",        r.isProfanity());
        response.put("matchedDocuments",   r.matchedDocuments());
        response.put("message",            r.isProfanity()
                ? "L2-Vector DB 유사도 검사에서 차단되었습니다."
                : "L2(RAG)를 통과하여 L3(LLM) 검토가 필요합니다.");
        return response;
    }
}
