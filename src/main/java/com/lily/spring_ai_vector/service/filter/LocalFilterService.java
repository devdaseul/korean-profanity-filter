package com.lily.spring_ai_vector.service.filter;

import com.lily.spring_ai_vector.service.support.BadWordFilter;
import com.lily.spring_ai_vector.service.support.TextNormalizer;
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

    /* L1 로컬 필터링 실행 (Step 1~4) */
    public Result check(String input) {
        log.info("[L1] ── 시작 ─────────────────────────────");

        // Step 1: 정규화
        String normalized = textNormalizer.normalize(input);
        log.info("[L1-Step1] 전처리 | 원문='{}' → 정규화='{}'", input, normalized);

        // Step 2: Regex — 탐지 시 즉시 Fast-Exit
        boolean regexHit = badWordFilter.matchesRegex(normalized);
        log.info("[L1-Step2] 정규식(Regex)    | {}", regexHit ? "★ 탐지 → Fast-Exit" : "통과");
        if (regexHit) {
            log.info("[L1] ── Fast-Exit (Step2-Regex) ──────────────────");
            return new Result(normalized, true, false, false, true);
        }

        // Step 3: 블랙리스트 1:1 매칭 — 탐지 시 즉시 Fast-Exit
        boolean blacklistHit = badWordFilter.matchesBlacklist(normalized);
        log.info("[L1-Step3] 블랙리스트 매칭  | {}", blacklistHit ? "★ 탐지 → Fast-Exit" : "통과");
        if (blacklistHit) {
            log.info("[L1] ── Fast-Exit (Step3-Blacklist) ───────────────");
            return new Result(normalized, false, true, false, true);
        }

        // Step 4: Fuzzy NFD N-Gram 매칭 — 탐지 시 즉시 Fast-Exit
        boolean fuzzyHit = badWordFilter.matchesFuzzy(normalized);
        log.info("[L1-Step4] Fuzzy NFD 매칭   | {}", fuzzyHit ? "★ 탐지 → Fast-Exit" : "통과");
        if (fuzzyHit) {
            log.info("[L1] ── Fast-Exit (Step4-Fuzzy) ─────────────────");
            return new Result(normalized, false, false, true, true);
        }

        log.info("[L1] ── 전 단계 통과 → L2(RAG) 진행 필요 ─────────────");
        return new Result(normalized, false, false, false, false);
    }    /* L2/L3에서도 동일한 정규화 적용을 위한 위임 메서드 */
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
        else if (r.regexHit())     detectedBy = "Step2-Regex";
        else if (r.blacklistHit()) detectedBy = "Step3-Blacklist";
        else                       detectedBy = "Step4-Fuzzy";

        Map<String, Object> stageResults = new LinkedHashMap<>();
        stageResults.put("step1Normalize", r.normalized());
        stageResults.put("step2Regex",     r.regexHit()     ? "비속어 탐지" : "통과");
        stageResults.put("step3Blacklist", r.blacklistHit() ? "비속어 탐지" : "통과");
        stageResults.put("step4Fuzzy",     r.fuzzyHit()     ? "비속어 탐지" : "통과");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("originalText", input);
        response.put("isProfanity",  r.isProfanity());
        response.put("detectedBy",   detectedBy);
        response.put("message",      r.isProfanity() ? "L1 로컬 필터에서 차단되었습니다." : "L1 통과 → L2(RAG) 필요");
        response.put("stageResults", stageResults);
        return response;
    }
}