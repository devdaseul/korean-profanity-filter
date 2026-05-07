package com.lily.spring_ai_vector.service.filter;

import com.lily.spring_ai_vector.service.filter.support.BadWordFilter;
import com.lily.spring_ai_vector.service.filter.support.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public record LocalResult(
            String normalized,
            boolean regexHit,
            boolean blacklistHit,
            boolean fuzzyHit,
            boolean isProfanity
    ) {}

    /** L1 로컬 필터링 실행 (Step 1~4) */
    public LocalResult check(String input) {

        // Step 1: 정규화
        String normalized = textNormalizer.normalize(input);
        log.info("[L1-Local] 정규화 완료: '{}'", normalized);

        // Step 2: Regex — 탐지 시 즉시 Fast-Exit
        boolean regexHit = badWordFilter.matchesRegex(normalized);
        log.info("[L1-Local] Step2 Regex 판정: {}", regexHit);
        if (regexHit) {
            return new LocalResult(normalized, true, false, false, true);
        }

        // Step 3: 블랙리스트 1:1 매칭 — 탐지 시 즉시 Fast-Exit
        boolean blacklistHit = badWordFilter.matchesBlacklist(normalized);
        log.info("[L1-Local] Step3 Blacklist 판정: {}", blacklistHit);
        if (blacklistHit) {
            return new LocalResult(normalized, false, true, false, true);
        }

        // Step 4: Fuzzy NFD 매칭 — 탐지 시 즉시 Fast-Exit
        boolean fuzzyHit = badWordFilter.matchesFuzzy(normalized);
        log.info("[L1-Local] Step4 Fuzzy 판정: {}", fuzzyHit);
        if (fuzzyHit) {
            return new LocalResult(normalized, false, false, true, true);
        }

        log.info("[L1-Local] 정상 통과");
        return new LocalResult(normalized, false, false, false, false);
    }

    /** L2/L3 정규화 위임 메서드 */
    public String normalize(String input) {
        return textNormalizer.normalize(input);
    }
}