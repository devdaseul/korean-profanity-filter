package com.lily.spring_ai_vector.service.filter.support;

import com.lily.spring_ai_vector.config.FilterPipelineProperties;
import com.lily.spring_ai_vector.repository.BadWordRepository;

import org.springframework.util.StringUtils;
import static org.apache.commons.lang3.StringUtils.replaceEach;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * [L1] Regex + Blacklist + Fuzzy 통합 필터
 *
 * DB 조회 1회로 blacklistCache(Set)와 nfdCache(List) 동시 구성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BadWordFilter {

    private final BadWordRepository badWordRepository;
    private final FilterPipelineProperties props;

    private static final Pattern CACHE_CLEAN_PATTERN = Pattern.compile("[^가-힣a-zA-Z0-9]");

    private Pattern badWordPattern;
    private Set<String> blacklistCache = new HashSet<>();
    private List<String> nfdCache = Collections.emptyList();

    private String[] searchList = new String[0];
    private String[] replacementList = new String[0];

    // Fuzzy 매칭 전용 (앱 기동 시 1회 초기화)
    private double fuzzyThreshold;
    private JaroWinklerSimilarity jaroWinkler;

    @PostConstruct
    public void initCache() {
        this.fuzzyThreshold = props.fuzzy().threshold();
        this.jaroWinkler = new JaroWinklerSimilarity();

        var replacements = props.normalize().visualReplacements();
        if (replacements != null && !replacements.isEmpty()) {
            searchList = replacements.keySet().toArray(new String[0]);
            replacementList = replacements.values().toArray(new String[0]);
        }

        this.badWordPattern = Pattern.compile(props.regex().pattern());
        log.info("[BadWordFilter] 정규식 패턴 로드 완료");

        List<String> words = badWordRepository.findAllActiveWords();
        this.blacklistCache = new HashSet<>(words.stream().map(this::normalizeForCache).toList());
        this.nfdCache = words.stream().map(this::toNfd).toList();

        log.info("[BadWordFilter] 캐시 로드 완료 (단어 수: {})", words.size());
    }

    public void refreshCache() {
        initCache();
    }

    /** [Step 2] 정규식 패턴 매칭 */
    public boolean matchesRegex(String text) {
        return badWordPattern.matcher(text).find();
    }

    /** [Step 3] 블랙리스트 1:1 매칭 */
    public boolean matchesBlacklist(String text) {
        if (!StringUtils.hasText(text) || blacklistCache.isEmpty()) return false;
        return Arrays.stream(text.split("\\s+"))
                .map(this::normalizeForCache)
                .anyMatch(blacklistCache::contains);
    }

    /**
     * [Step 4] Jaro-Winkler 유사도 + NFD 자모 분리 퍼즈매칭
     */
    public boolean matchesFuzzy(String normalizedText) {
        if (!StringUtils.hasText(normalizedText)) return false;

        // Fuzzy 검사를 위해 공백과 특수문자를 모두 제거하여 완전히 압축 ("시 발" -> "시발")
        String compressedText = CACHE_CLEAN_PATTERN.matcher(normalizedText).replaceAll("");
        String inputNfd = toNfd(compressedText);
        int inputLen = inputNfd.length();

        for (String badNfd : nfdCache) {
            int badLen = badNfd.length();
            if (inputLen < badLen) continue;

            // Fast-Exit: 자모열 완전 포함 시 즉시 차단
            if (inputNfd.contains(badNfd)) return true;

            // 슬라이딩 윈도우: bad word 자모 길이 고정 크기로 Jaro-Winkler 유사도 검사
            for (int i = 0; i <= inputLen - badLen; i++) {
                String window = inputNfd.substring(i, i + badLen);
                if (jaroWinkler.apply(window, badNfd) >= fuzzyThreshold) {
                    log.debug("[Fuzzy] 매칭: 윈도우=\"{}\" ↔ 욕설=\"{}\"", window, badNfd);
                    return true;
                }
            }
        }
        return false;
    }

    /** 블랙리스트 키 생성용 경량 정규화 (시각적 치환 + 특수문자 제거) */
    public String normalizeForCache(String input) {
        if (!StringUtils.hasText(input)) return "";

        String text = replaceEach(input.toLowerCase(), searchList, replacementList);
        return CACHE_CLEAN_PATTERN.matcher(text).replaceAll("");
    }

    /** 한글을 NFD 자모 단위로 분해 */
    private String toNfd(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD);
    }
}
