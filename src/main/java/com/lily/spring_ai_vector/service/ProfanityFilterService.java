package com.lily.spring_ai_vector.service;

import com.lily.spring_ai_vector.dto.MaskFilterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * [API 2] RAG 기반 비속어 마스킹 서비스
 *
 * 동작 원리:
 *  1. 사용자 입력 문장을 토큰(단어) 단위로 분리
 *  2. 각 토큰(또는 연속 2-gram)을 VectorStore 에서 유사도 검색
 *  3. 유사도가 임계값(threshold) 이상인 토큰을 비속어로 판정
 *  4. 판정된 비속어를 '***' 로 마스킹하여 반환
 *
 * RAG 마스킹을 선택한 이유:
 *  - 정규식 필터: 변형 표현(시1발, ㅅㅂ 등) 대응이 어려움
 *  - 단순 사전 매칭: 철자 변형·신조어 대응 불가
 *  - 벡터 유사도: 임베딩 공간에서 의미/형태가 유사한 변형 표현까지 탐지 가능
 *
 * 한계:
 *  - "개"(단독)처럼 단어 분리 시 오탐 가능성 있음 → threshold 조정으로 완화
 *  - 완전한 문장 수준 변형은 API 3(LLM)으로 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfanityFilterService {

    private final VectorStore vectorStore;

    /**
     * RAG 유사도 검색 임계값
     *
     * 설정 근거:
     *  - 0.80 이상: 거의 동일한 표현만 탐지 (오탐↓, 미탐↑)
     *  - 0.75: 변형 표현까지 탐지하는 균형점 (현재 설정)
     *  - 0.70 이하: 무관한 단어까지 탐지될 위험 (오탐↑)
     */
    private static final double SIMILARITY_THRESHOLD = 0.75;

    /**
     * RAG 검색 시 반환할 상위 문서 수
     * 단어 하나당 최대 3개의 유사 후보를 조회하여 판정
     */
    private static final int TOP_K = 3;

    /**
     * 비속어 마스킹 처리 메인 메서드
     *
     * @param userInput 사용자 입력 원문
     * @return 마스킹 결과 및 탐지된 비속어 목록
     */
    public MaskFilterResponse mask(String userInput) {
        long startTime = System.currentTimeMillis();

        // 1. 입력을 공백 단위로 토큰화
        //    왜 공백 분리? → 한국어는 어절 단위가 의미의 기본 단위
        //    향후 형태소 분석기(KoNLPy) 도입 시 이 부분을 교체
        String[] tokens = userInput.split("\\s+");

        List<String> detectedWords = new ArrayList<>();
        String[] maskedTokens = new String[tokens.length];

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];

            // 2. 토큰별 벡터 유사도 검색
            boolean isProfanity = isProfanity(token);

            if (isProfanity) {
                detectedWords.add(token);
                // 3. 마스킹: 원단어 길이에 따라 '*' 개수 결정
                //    왜 길이 기반? → 단어 형태를 완전히 숨기면서 위치 정보는 보존
                maskedTokens[i] = "*".repeat(Math.max(token.length(), 3));
            } else {
                maskedTokens[i] = token;
            }
        }

        // 2-gram 추가 탐지: 연속된 두 토큰이 합쳐서 비속어인 경우
        // 예) "개" + "새끼" 처럼 분리된 경우
        for (int i = 0; i < maskedTokens.length - 1; i++) {
            // 이미 마스킹된 토큰은 건너뜀
            if (maskedTokens[i].startsWith("*") || maskedTokens[i + 1].startsWith("*")) continue;

            String bigram = tokens[i] + tokens[i + 1];
            if (isProfanity(bigram)) {
                detectedWords.add(bigram);
                maskedTokens[i] = "*".repeat(Math.max(tokens[i].length(), 3));
                maskedTokens[i + 1] = "*".repeat(Math.max(tokens[i + 1].length(), 3));
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        String maskedText = String.join(" ", maskedTokens);

        log.info("[RAG 마스킹] 입력=\"{}\" | 감지={} | 마스킹=\"{}\" | {}ms",
                userInput, detectedWords, maskedText, elapsed);

        return new MaskFilterResponse(
                userInput,
                maskedText,
                detectedWords,
                !detectedWords.isEmpty(),
                elapsed
        );
    }

    /**
     * 단일 토큰에 대한 비속어 판정
     *
     * @param token 판정할 단어/토큰
     * @return 비속어 여부
     *
     * 동작:
     *  - VectorStore 에서 topK=3 후보를 검색
     *  - 임계값 이상의 유사도를 가진 결과가 1개라도 있으면 비속어로 판정
     *  - distance 는 pgvector 코사인 거리 (0에 가까울수록 유사)
     *    Spring AI 의 similarityThreshold 는 1-distance 기반이므로 주의
     */
    private boolean isProfanity(String token) {
        if (token == null || token.length() < 1) return false;

        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(token)
                            .topK(TOP_K)
                            .similarityThreshold(SIMILARITY_THRESHOLD)
                            .build()
            );

            if (!results.isEmpty()) {
                log.debug("[비속어 탐지] 토큰=\"{}\" → 유사 단어: {}",
                        token,
                        results.stream().map(Document::getText).collect(Collectors.joining(", ")));
                return true;
            }
        } catch (Exception e) {
            log.warn("[벡터 검색 오류] 토큰=\"{}\": {}", token, e.getMessage());
        }
        return false;
    }
}
