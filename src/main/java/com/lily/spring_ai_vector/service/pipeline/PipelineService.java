package com.lily.spring_ai_vector.service.pipeline;

import com.lily.spring_ai_vector.dto.PipelineResponse;
import com.lily.spring_ai_vector.service.filter.LlmFilterService;
import com.lily.spring_ai_vector.service.filter.LocalFilterService;
import com.lily.spring_ai_vector.service.filter.RagFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L1 → L2 → L3 파이프라인 오케스트레이터
 *
 * L1(로컬: Regex → Blacklist → Fuzzy) → L2(RAG 유사도) → L3(LLM 판정) 순서로
 * 각 단계에서 비속어가 탐지되면 이후 단계는 스킵(Fast-Exit)하고 즉시 결과 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final LocalFilterService localFilterService;
    private final RagFilterService ragFilterService;
    private final LlmFilterService llmFilterService;

    @Transactional
    public PipelineResponse run(String userInput) {
        long start = System.currentTimeMillis();
        log.info("[Pipeline] ════════════════════════════════════");
        log.info("[Pipeline] START | 입력: '{}'", userInput);
        log.info("[Pipeline] ════════════════════════════════════");

        // ── L1: Local Filter ──────────────────────────────────
        log.info("[Pipeline] → L1(로컬 필터) 진입");
        LocalFilterService.Result l1 = localFilterService.check(userInput);
        if (l1.isProfanity()) {
            log.info("[Pipeline] ★ L1에서 차단 | 소요: {}ms", elapsed(start));
            log.info("[Pipeline] ════════════════════════════════════");
            return buildFastExitResponse(userInput, l1.normalized(), "L1-Local", start,
                            l1.regexHit() ? "탐지" : "통과",
                    l1.blacklistHit() ? "탐지" : "통과",
                    l1.fuzzyHit() ? "탐지" : "통과",
                    "스킵", "스킵");
        }
        log.info("[Pipeline] ✓ L1 통과 → L2(RAG) 진입");

        // ── L2: RAG 유사도 검색 ───────────────────────────────
        RagFilterService.Result l2 = ragFilterService.check(l1.normalized());
        if (l2.isProfanity()) {
            log.info("[Pipeline] ★ L2에서 차단 | 소요: {}ms", elapsed(start));
            log.info("[Pipeline] ════════════════════════════════════");
            return buildFastExitResponse(userInput, l1.normalized(), "L2-RAG", start,
                            "통과", "통과", "통과", "탐지", "스킵");
        }
        log.info("[Pipeline] ✓ L2 통과 → L3(LLM) 진입");

        // ── L3: LLM 최종 판단 ────────────────────────────────
        LlmFilterService.Result l3 = llmFilterService.check(l1.normalized());

        log.info("[Pipeline] ════════════════════════════════════");
        log.info("[Pipeline] FINISH | {} | 총 소요: {}ms",
                l3.isProfanity() ? "★ PROFANITY" : "SAFE", elapsed(start));
        log.info("[Pipeline] ════════════════════════════════════");        return PipelineResponse.ofLlm(
                userInput, 
                l1.normalized(), 
                l3.isProfanity(), 
                elapsed(start),
                new PipelineResponse.StageResults(
                        l1.normalized(), 
                        "통과", "통과", "통과", "통과", // Regex, Blacklist, Fuzzy, RAG 모두 통과
                        l3.llmRaw() // LLM 결과
                )
        );
    }

    private PipelineResponse buildFastExitResponse(String input, String norm, String step, long start, String... res) {
        return PipelineResponse.ofFastExit(input, norm, step, elapsed(start),
                new PipelineResponse.StageResults(norm, res[0], res[1], res[2], res[3], res[4]));
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}