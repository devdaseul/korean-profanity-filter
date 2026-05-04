package com.lily.spring_ai_vector.service.pipeline;

import com.lily.spring_ai_vector.dto.PipelineResponse;
import com.lily.spring_ai_vector.service.filter.LlmFilterService;
import com.lily.spring_ai_vector.service.filter.LocalFilterService;
import com.lily.spring_ai_vector.service.filter.RagFilterService;
import com.lily.spring_ai_vector.enums.StageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * L1 → L2 → L3 파이프라인 오케스트레이터
 *
 * 각 단계에서 탐지되면 이후 단계는 스킵(Fast-Exit)하고 즉시 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final LocalFilterService localFilterService;
    private final RagFilterService ragFilterService;
    private final LlmFilterService llmFilterService;

    public PipelineResponse run(String userInput) {
        long start = System.currentTimeMillis();
        log.info("[Pipeline] 시작 | 입력: '{}'", userInput);

        // L1: Local Filter
        log.info("[Pipeline] L1(로컬 필터) 진입");
        LocalFilterService.Result l1 = localFilterService.check(userInput);
        if (l1.isProfanity()) {
            log.info("[Pipeline] L1 차단 | 소요: {}ms", elapsed(start));
            return buildFastExitResponse(userInput, l1.normalized(), "L1-Local", start,
                    l1.regexHit()     ? StageStatus.DETECTED.getLabel() : StageStatus.PASSED.getLabel(),
                    l1.blacklistHit() ? StageStatus.DETECTED.getLabel() : StageStatus.PASSED.getLabel(),
                    l1.fuzzyHit()     ? StageStatus.DETECTED.getLabel() : StageStatus.PASSED.getLabel(),
                    StageStatus.SKIPPED.getLabel(), StageStatus.SKIPPED.getLabel());
        }
        log.info("[Pipeline] L1 통과 -> L2(RAG) 진입");

        // L2: RAG 유사도 검색
        // L1에서 생성된 정규화 텍스트와 원문을 함께 넘겨줌
        RagFilterService.Result l2 = ragFilterService.check(userInput, l1.normalized());
        if (l2.isProfanity()) {
            log.info("[Pipeline] L2 차단 | 소요: {}ms", elapsed(start));
            return PipelineResponse.ofRag(userInput, l1.normalized(), elapsed(start),
                    new PipelineResponse.StageResults(
                            l1.normalized(),
                            StageStatus.PASSED.getLabel(), StageStatus.PASSED.getLabel(),
                            StageStatus.PASSED.getLabel(), StageStatus.DETECTED.getLabel(),
                            StageStatus.SKIPPED.getLabel()));
        }

        log.info("[Pipeline] L2 통과 -> L3(LLM) 진입");

        // L3: LLM 최종 판단
        // 원문과 정규화 텍스트를 함께 넘겨주어 정확한 판정 지원
        LlmFilterService.Result l3 = llmFilterService.check(userInput, l1.normalized());

        log.info("[Pipeline] 종료 | {} | 총 소요: {}ms",
                l3.isProfanity() ? "차단(PROFANITY)" : "통과(SAFE)", elapsed(start));
        return PipelineResponse.ofLlm(
                userInput, 
                l1.normalized(), 
                l3.isProfanity(), 
                elapsed(start),
                new PipelineResponse.StageResults(
                        l1.normalized(),
                        StageStatus.PASSED.getLabel(), StageStatus.PASSED.getLabel(),
                        StageStatus.PASSED.getLabel(), StageStatus.PASSED.getLabel(),
                        l3.judgeOutput() != null ? l3.judgeOutput() : StageStatus.SKIPPED.getLabel()
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