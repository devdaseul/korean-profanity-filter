package com.lily.spring_ai_vector.service.filter;

import com.lily.spring_ai_vector.entity.LlmJudgeLog;
import com.lily.spring_ai_vector.repository.LlmJudgeLogRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [L3] LLM 기반 최종 판정 서비스
 *
 * 모든 이전 단계를 통과한 모호한 케이스를 LLM이 최종 판단하고 로그를 저장
 *
 * ▶ llmChatClient (순환참조 방지):
 *   일반 chatClient 를 쓰면 ProfanityAroundAdvisor → PipelineService → 여기로 돌아와 무한루프 발생
 *   llmChatClient 는 Advisor 미등록 빈이라 루프 없이 LLM 직접 호출
 */
@Slf4j
@Service
public class LlmFilterService {

    private final ChatClient llmChatClient;
    private final LlmJudgeLogRepository llmJudgeLogRepository;
    private final LocalFilterService localFilterService;

    public LlmFilterService(@Qualifier("llmChatClient") ChatClient llmChatClient,
                            LlmJudgeLogRepository llmJudgeLogRepository,
                            LocalFilterService localFilterService) {
        this.llmChatClient = llmChatClient;
        this.llmJudgeLogRepository = llmJudgeLogRepository;
        this.localFilterService = localFilterService;
    }

    @Value("classpath:prompts/profanity-judge-system.st")
    private Resource systemPromptResource;

    public record Result(
            String originalText,
            String normalized,
            String llmRaw,
            boolean isProfanity
    ) {}

    @Transactional
    public Result check(String input) {
        log.info("[L3-LLM] ── 시작 ────────────────────────────");

        // [1] 입력값 검증
        if (input == null || input.isBlank()) {
            log.warn("[L3-LLM] 입력 텍스트가 비어있어 SAFE로 조기 반환합니다.");
            return new Result(input, "", "RESULT:SAFE", false);
        }

        String normalized = localFilterService.normalize(input);
        log.info("[L3-LLM] 정규화 결과: '{}'", normalized);

        // [2] 정규화 후 텍스트가 사라진 경우 처리 (예: "!!!!"만 입력한 경우)
        if (normalized == null || normalized.isBlank()) {
            normalized = input;
        }

        log.info("[L3-LLM] Ollama(llama3) 호출 시작 → 프롬프트 전송 중...");
        String llmRaw = llmChatClient.prompt()
                .system(sp -> sp.text(systemPromptResource).param("context", "없음"))
                .user(normalized)
                .call()
                .content();
        log.info("[L3-LLM] Ollama 응답 수신 | 원문: '{}'", llmRaw);

        // LLM 응답 파싱
        boolean isProfanity = parseIsProfanity(llmRaw);
        String reason = parseReason(llmRaw);
        String category = parseCategory(llmRaw);
        Integer severity = parseSeverity(llmRaw);
        log.info("[L3-LLM] 파싱 결과 | isProfanity={} category={} severity={} reason='{}'",
                isProfanity, category, severity, reason);

        // llm_judge_log 저장 (is_trained=false → 관리자 검토 대기)
        LlmJudgeLog saved = llmJudgeLogRepository.save(LlmJudgeLog.builder()
                .originalText(input)
                .normalizedText(normalized)
                .llmResult(isProfanity ? "PROFANITY" : "SAFE")
                .llmReason(reason)
                .category(category)
                .severity(severity)
                .isTrained(false)
                .build());
        log.info("[L3-LLM] llm_judge_log 저장 완료 | id={} isTrained=false (관리자 검토 대기)", saved.getId());

        log.info("[L3-LLM] ── 완료 | {} ─────────────────────────", isProfanity ? "★ PROFANITY" : "SAFE");
        return new Result(input, normalized, llmRaw, isProfanity);
    }

    public Map<String, Object> checkAsMap(String input) {
        Result r = check(input);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("originalText", r.originalText());
        response.put("llmResponse",  r.llmRaw());
        response.put("isProfanity",  r.isProfanity());
        response.put("message",      r.isProfanity()
                ? "L3-LLM에서 최종 비속어로 판정되어 DB에 기록(보류상태)되었습니다."
                : "최종 안전한 문장으로 판정되었습니다.");
        return response;
    }

    // ── LLM 응답 파서 (LlmFilterService 전용) ─────────────────────

    private static final java.util.regex.Pattern P_RESULT   = java.util.regex.Pattern.compile("RESULT:(PROFANITY|SAFE)");
    private static final java.util.regex.Pattern P_REASON   = java.util.regex.Pattern.compile("REASON:(.*?)(?=CATEGORY:|SEVERITY:|$)", java.util.regex.Pattern.DOTALL);
    private static final java.util.regex.Pattern P_CATEGORY = java.util.regex.Pattern.compile("CATEGORY:(\\w+)");
    private static final java.util.regex.Pattern P_SEVERITY = java.util.regex.Pattern.compile("SEVERITY:(\\d)");

    private static boolean parseIsProfanity(String raw) {
        var m = P_RESULT.matcher(raw);
        return m.find() && "PROFANITY".equals(m.group(1));
    }

    private static String parseReason(String raw) {
        var m = P_REASON.matcher(raw);
        return m.find() ? m.group(1).trim() : "사유 없음";
    }

    private static String parseCategory(String raw) {
        var m = P_CATEGORY.matcher(raw);
        return m.find() ? m.group(1) : "UNCATEGORIZED";
    }

    private static Integer parseSeverity(String raw) {
        var m = P_SEVERITY.matcher(raw);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
