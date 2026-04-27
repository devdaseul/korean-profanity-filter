package com.lily.spring_ai_vector.service.support;

import com.lily.spring_ai_vector.config.FilterPipelineProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * [전처리 Step 1] 텍스트 정규화
 *
 * ▶ 교육/실습 L1 요구사항 반영:
 *   1. 특수문자 전면 제거, 공백(ZWSP 포함) 전면 밀착
 *   2. 숫자 변환(7->ㄱ), 시각적 변환(r->ㅏ, L->ㄴ) 
 *   3. 영타 Qwerty 변환 (tlqkf -> ㅅㅣㅂㅏㄹ)
 *   4. NFC 정규화 가동 (자모 취합 시도)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextNormalizer {

    private final FilterPipelineProperties props;
    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^a-zA-Z0-9가-힣ㄱ-ㅎㅏ-ㅣ]");

    public String normalize(String input) {
        if (input == null || input.isBlank()) return "";

        // 1. 노이즈 제거: 공백 및 특수문자 전면 제거
        String text = input.replaceAll("[\\p{Z}\\p{C}\\s]+", "");
        text = CLEAN_PATTERN.matcher(text).replaceAll("");

        // 2. 정책 치환: YAML에 등록된 시각적 유사 기호 처리 (7 -> ㄱ 등)
        text = applyVisualReplacements(text);

        // 3. 언어 변환: 영타를 한글 자모로 변환 (tlqkf -> ㅅㅣㅂㅏㄹ)
        text = qwertyToJamo(text);

        // 4. 최종 정규화: 소문자화 및 반복 글자 압축
        text = text.toLowerCase();
        text = Normalizer.normalize(text, Normalizer.Form.NFC); // 표준화만 수행

        log.debug("[Normalize] {} -> {}", input, text);
        return compressRepeat(text, props.normalize().maxRepeat());
    }

    private String applyVisualReplacements(String text) {
        var replacements = props.normalize().visualReplacements();
        if (replacements == null) return text;
        
        for (var entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    private String qwertyToJamo(String text) {
        var qwerty = props.normalize().qwerty();
        if (qwerty == null) return text;

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            int idx = qwerty.eng().indexOf(c);
            sb.append(idx != -1 ? qwerty.kor().charAt(idx) : c);
        }
        return sb.toString();
    }

    private String compressRepeat(String text, int maxRepeat) {
        if (maxRepeat < 1) return text;
        return text.replaceAll("(.)\\1{" + maxRepeat + ",}", "$1".repeat(maxRepeat));
    }
}
