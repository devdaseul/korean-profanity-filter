package com.lily.spring_ai_vector.service;

import com.lily.spring_ai_vector.dto.RefineResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * [API 3] RAG + LLM 비속어 순화 서비스
 *
 * 동작 원리:
 *  1. VectorStore 에서 입력 문장과 유사한 비속어 후보를 RAG 검색
 *  2. 검색된 후보를 System Prompt 의 [유사 비속어 목록] 에 주입 (RAG 컨텍스트)
 *  3. LLM(llama3)이 목록을 참고하여 비속어를 탐지하고 순화된 문장으로 변환
 *
 * API 2(마스킹)와의 차이:
 *  - API 2: 빠른 실시간 필터링, 비속어를 *** 로 치환 (단순 마스킹)
 *  - API 3: LLM 이 문맥을 이해하여 자연스러운 대체 표현 생성 (느리지만 고품질)
 *
 * 설계 핵심:
 *  - RAG 컨텍스트를 LLM 에 주입함으로써 "환각(hallucination)" 방지
 *  - 비속어 목록 없이 LLM 에만 의존하면 철자 변형 탐지가 불안정
 *  - RAG 가 "이 단어들을 봐라" 힌트를 주면 LLM 정확도가 크게 향상
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfanityRefineService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    /**
     * RAG 유사도 임계값 (API 3)
     *
     * API 2보다 낮게 설정한 이유:
     *  - LLM 이 최종 판단하므로 RAG 는 후보를 넉넉히 수집해도 됨
     *  - 약간 낮은 임계값으로 더 많은 비속어 힌트를 LLM 에 제공
     *  - LLM 이 최종 필터 역할이므로 RAG 의 오탐이 순화 결과에 직접 영향 없음
     */
    private static final double SIMILARITY_THRESHOLD = 0.70;
    private static final int TOP_K = 5;

    /**
     * 비속어 순화 메인 메서드
     *
     * @param userInput 사용자 입력 원문
     * @return 순화된 문장 및 성능 지표
     */
    public RefineResponse refine(String userInput) {
        long totalStart = System.currentTimeMillis();

        // ── Step 1: RAG 검색 ──────────────────────────────
        // 입력 문장 전체를 쿼리로 사용하여 의미적으로 유사한 비속어 후보 수집
        // 왜 전체 문장? → 단어 분리 없이 문장 임베딩으로 검색하면
        //   "오늘 개노잼이네" 같은 복합 표현도 한 번에 포착 가능
        long ragStart = System.currentTimeMillis();

        List<Document> ragResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userInput)
                        .topK(TOP_K)
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .build()
        );

        long ragMs = System.currentTimeMillis() - ragStart;

        // RAG 결과를 LLM 프롬프트에 주입할 컨텍스트 문자열로 변환
        String ragContext = ragResults.stream()
                .map(Document::getText)
                .collect(Collectors.joining(", "));

        log.info("[RAG 검색] 입력=\"{}\" | 후보=({}) | {}ms",
                userInput, ragContext, ragMs);

        // ── Step 2: LLM 순화 ──────────────────────────────
        long llmStart = System.currentTimeMillis();

        String refined = chatClient.prompt()
                .system(sp -> sp.text("""
                        당신은 부드럽고 따뜻한 말투의 '언어 순화 상담사'입니다.
                        다음 지침에 따라 사용자의 문장을 아름다운 우리말로 바꾸세요.

                        [지침]
                        - 제공된 [유사 비속어 목록]을 참고하여 문장 내 부적절한 표현을 찾아내세요.
                        - 비속어의 감정적 의도는 유지하되, 표현은 반드시 정중한 표준어로 변경하세요.
                        - 철자 변형(예: 시1발, ㅅㅂ, 개노잼)도 비속어로 처리하세요.
                        - **절대로 초성(ㅅㅊ, ㅂㅂ 등)이나 단답형으로 대답하지 마세요.**
                        - 입력이 안전한 문장이라면 원문을 그대로 출력하세요.
                        - 반드시 순화된 문장만 출력하세요. 설명·인사말·따옴표 금지.
                        - "You meant to say" 같은 영어 표현도 절대 포함하지 마세요.

                        [유사 비속어 목록]
                        {context}
                        """).param("context", ragContext.isEmpty() ? "없음" : ragContext))
                .user(userInput)
                .call()
                .content();

        long llmMs = System.currentTimeMillis() - llmStart;
        long totalMs = System.currentTimeMillis() - totalStart;

        log.info("[LLM 순화] 원문=\"{}\" → 순화=\"{}\" | RAG={}ms, LLM={}ms, 합계={}ms",
                userInput, refined, ragMs, llmMs, totalMs);

        return new RefineResponse(userInput, refined, ragMs, llmMs, totalMs);
    }
}
