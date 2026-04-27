package com.lily.spring_ai_vector.service.support;

import com.lily.spring_ai_vector.config.FilterPipelineProperties;
import com.lily.spring_ai_vector.repository.BadWordRepository;
import info.debatty.java.stringsimilarity.Jaccard;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * [L1] 비속어 단어 목록 기반 필터 (Regex + Blacklist + Fuzzy 통합)
 *
 * ▶ 통합 이유:
 *   FastExitFilterService(Regex/Blacklist)와 FuzzyMatcher 모두 BadWordRepository에서
 *   동일한 단어 목록을 로드하므로, DB 조회를 1회로 줄이고 캐시를 단일 관리
 *
 * ▶ 캐시 구조:
 *   - blacklistCache : 정규화된 단어 Set → Regex·Blacklist 검사용
 *   - nfdCache       : NFD 자모분리된 단어 List → Fuzzy N-Gram 검사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BadWordFilter {

    private final BadWordRepository badWordRepository;
    private final FilterPipelineProperties props;

    private static final int NGRAM_SIZE = 2;

    private Pattern badWordPattern;
    private Set<String> blacklistCache = new HashSet<>();
    private List<String> nfdCache = Collections.emptyList();

    @PostConstruct
    public void initCache() {
        // 정규식 패턴 컴파일
        if (props.regex() != null && props.regex().pattern() != null) {
            this.badWordPattern = Pattern.compile(props.regex().pattern());
            log.info("[BadWordFilter] 정규식 패턴 로드 완료");
        } else {
            log.warn("[BadWordFilter] regex.pattern 설정이 누락되었습니다.");
        }

        // DB 단어 목록 1회 조회 후 두 캐시에 동시 반영
        List<String> words = badWordRepository.findAllActiveWords();
        this.blacklistCache = new HashSet<>(words.stream().map(this::normalizeForCache).toList());
        this.nfdCache = words.stream().map(this::toNfd).toList();

        log.info("[BadWordFilter] 캐시 로드 완료 (단어 수: {})", words.size());
    }

    public void refreshCache() {
        log.info("[BadWordFilter] 캐시 갱신 시작");
        initCache();
    }

    // ── Step 2 ────────────────────────────────────────────────────

    /** [Step 2] 정규식 패턴 매칭 */
    public boolean matchesRegex(String text) {
        return badWordPattern != null && badWordPattern.matcher(text).find();
    }

    // ── Step 3 ────────────────────────────────────────────────────

    /** [Step 3] 블랙리스트 1:1 매칭 */
    public boolean matchesBlacklist(String text) {
        if (text == null || blacklistCache.isEmpty()) return false;
        for (String token : text.split("\\s+")) {
            if (blacklistCache.contains(normalizeForCache(token))) return true;
        }
        return false;
    }

    // ── Step 4 ────────────────────────────────────────────────────

    /**
     * [Step 4] Fuzzy NFD N-Gram 매칭
     *
     * @param normalizedText 전처리된 텍스트
     */
    public boolean matchesFuzzy(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) return false;

        String inputNfd = toNfd(normalizedText);
        double threshold = props.fuzzy() != null ? props.fuzzy().threshold() : 0.85;
        Jaccard jaccard = new Jaccard(NGRAM_SIZE);

        for (String badNfd : nfdCache) {
            // Fast-Exit: 자모열 완전 포함 시 즉시 차단
            if (inputNfd.contains(badNfd)) return true;

            int bLen = badNfd.length();
            if (bLen > inputNfd.length()) {
                if (jaccard.similarity(inputNfd, badNfd) >= threshold) return true;
                continue;
            }

            // 슬라이딩 윈도우: bad word 길이 ±1 크기로 잘라가며 N-Gram 유사도 검사
            for (int w = Math.max(1, bLen - 1); w <= Math.min(inputNfd.length(), bLen + 1); w++) {
                for (int i = 0; i <= inputNfd.length() - w; i++) {
                    String slice = inputNfd.substring(i, i + w);
                    double score = jaccard.similarity(slice, badNfd);
                    if (score >= threshold) {
                        log.debug("[Fuzzy] 매칭: 윈도우=\"{}\" ↔ 욕설=\"{}\" (유사도={})", slice, badNfd, score);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────

    /**
     * [캐시용 전처리] 블랙리스트 키 생성 전용 경량 정규화
     * TextNormalizer.normalize()와 달리 시각적 치환 + 특수문자 제거만 수행
     */
    public String normalizeForCache(String input) {
        if (input == null || input.isBlank()) return "";
        String text = input.toLowerCase();
        Map<String, String> replacements = props.normalize().visualReplacements();
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
        }
        return text.replaceAll("[^가-힣a-zA-Z0-9]", "");
    }

    /** 한글을 NFD 자모 단위로 분해 */
    private String toNfd(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD);
    }
}
