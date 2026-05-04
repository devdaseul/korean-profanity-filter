package com.lily.spring_ai_vector.service.filter;

import com.lily.spring_ai_vector.enums.StageStatus;
import com.lily.spring_ai_vector.service.filter.support.BadWordFilter;
import com.lily.spring_ai_vector.service.filter.support.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [L1] 전처리 + 로컬 필터링 통합 서비스
 *
 * 단계별 책임은 각 클래스가 담당(SRP), 이 클래스는 L1 흐름만 조율
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFilterService {

    private final TextNormalizer textNormalizer;
    private final BadWordFilter badWordFilter;

    public record Result(
            String normalized,
            boolean regexHit,
            boolean blacklistHit,
            boolean fuzzyHit,
            boolean isProfanity
    ) {}

    /** L1 로컬 필터링 실행 (Step 1~4) */
    public Result check(String input) {

        // Step 1: 정규화
        String normalized = textNormalizer.normalize(input);
        log.info("[L1-Local] 정규화 완료: '{}'", normalized);

        // Step 2: Regex — 탐지 시 즉시 Fast-Exit
        boolean regexHit = badWordFilter.matchesRegex(normalized);
        log.info("[L1-Local] Step2 Regex 판정: {}", regexHit);
        if (regexHit) {
            return new Result(normalized, true, false, false, true);
        }

        // Step 3: 블랙리스트 1:1 매칭 — 탐지 시 즉시 Fast-Exit
        boolean blacklistHit = badWordFilter.matchesBlacklist(normalized);
        log.info("[L1-Local] Step3 Blacklist 판정: {}", blacklistHit);
        if (blacklistHit) {
            return new Result(normalized, false, true, false, true);
        }

        // Step 4: Fuzzy NFD 매칭 — 탐지 시 즉시 Fast-Exit
        boolean fuzzyHit = badWordFilter.matchesFuzzy(normalized);
        log.info("[L1-Local] Step4 Fuzzy 판정: {}", fuzzyHit);
        if (fuzzyHit) {
            return new Result(normalized, false, false, true, true);
        }

        log.info("[L1-Local] 정상 통과");
        return new Result(normalized, false, false, false, false);
    }

    /** L2/L3 정규화 위임 메서드 */
    public String normalize(String input) {
        return textNormalizer.normalize(input);
    }

    /**
     * L1 결과를 API 응답용 Map으로 조립
     */
    public Map<String, Object> checkAsMap(String input) {
        Result r = check(input);

        String detectedBy;
        if (!r.isProfanity())      detectedBy = "미탐지 → L2(RAG) 필요";
        else if (r.regexHit())     detectedBy = "L1-Regex";
        else if (r.blacklistHit()) detectedBy = "L1-Blacklist";
        else                       detectedBy = "L1-Fuzzy";

        Map<String, Object> stageResults = new LinkedHashMap<>();
        stageResults.put("step1Normalize", r.normalized());
        stageResults.put("step2Regex",     r.regexHit()     ? StageStatus.DETECTED.getLabel() : StageStatus.PASSED.getLabel());
        stageResults.put("step3Blacklist", r.blacklistHit() ? StageStatus.DETECTED.getLabel() : StageStatus.PASSED.getLabel());
        stageResults.put("step4Fuzzy",     r.fuzzyHit()     ? StageStatus.DETECTED.getLabel() : StageStatus.PASSED.getLabel());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("originalText", input);
        response.put("isProfanity",  r.isProfanity());
        response.put("detectedBy",   detectedBy);
        response.put("message",      r.isProfanity() ? "L1 로컬 필터에서 차단되었습니다." : "L1 통과 → L2(RAG) 필요");
        response.put("stageResults", stageResults);
        return response;
    }
}