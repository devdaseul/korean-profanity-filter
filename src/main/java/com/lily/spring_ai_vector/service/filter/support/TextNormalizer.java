package com.lily.spring_ai_vector.service.filter.support;

import com.lily.spring_ai_vector.config.FilterPipelineProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * [Step 1] 텍스트 전처리 (Normalize)
 *
 * 1. 노이즈 제거 (제어 문자만 제거, 공백/특수문자 보존)
 * 2. 시각적 치환 (7→ㄱ, r→ㅡ 등 YAML 정의)
 * 3. Qwerty → 한글 자모 변환 (tlqkf → ㅅㅣㅂㅐㄹ)
 * 4. NFKC 정규화 (전각→반각 + 자모 표준화)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextNormalizer {

    private final FilterPipelineProperties props;

    // 공백과 특수문자를 보존하도록 정규식 수정
    private static final Pattern NOISE_PATTERN = Pattern.compile("[\\p{C}]+"); // 제어 문자만
    private static final Pattern SPACE_PATTERN = Pattern.compile("[\\p{Z}\\s]+"); // 연속된 공백 압축용

    private String[] searchList        = new String[0];
    private String[] replacementList   = new String[0];
    private Pattern  repeatPattern;
    private String   repeatReplacement;

    @PostConstruct
    public void init() {
        var replacements = props.normalize().visualReplacements();
        if (replacements != null && !replacements.isEmpty()) {
            searchList      = replacements.keySet().toArray(new String[0]);
            replacementList = replacements.values().toArray(new String[0]);
        }
        int max = props.normalize().maxRepeat();
        if (max >= 1) {
            repeatPattern    = Pattern.compile("(.)\\1{" + max + ",}");
            repeatReplacement = "$1".repeat(max);
        }
    }

    public String normalize(String input) {
        if (StringUtils.isBlank(input)) return "";

        // 1. 노이즈 제거 (제어 문자 제거 및 여러 공백을 하나로 압축. 특수기호는 보존!)
        String text = NOISE_PATTERN.matcher(input).replaceAll("");
        text = SPACE_PATTERN.matcher(text).replaceAll(" ").trim();

        // 2. 시각적 치환
        text = StringUtils.replaceEach(text, searchList, replacementList);

        // 3. Qwerty → 자모 변환
        text = qwertyToJamo(text);

        // 4. NFKC 정규화
        text = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFKC);
        log.debug("[Normalize] {} -> {}", input, text);
        
        return repeatPattern != null
                ? repeatPattern.matcher(text).replaceAll(repeatReplacement)
                : text;
    }

    private String qwertyToJamo(String text) {
        var qwerty = props.normalize().qwerty();
        if (qwerty == null) return text;

        String eng = qwerty.eng();
        String kor = qwerty.kor();
        StringBuilder sb = new StringBuilder();

        for (char c : text.toCharArray()) {
            int idx = eng.indexOf(c);
            sb.append(idx != -1 ? kor.charAt(idx) : c);
        }
        return sb.toString();
    }
}

