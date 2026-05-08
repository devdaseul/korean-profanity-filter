package com.lily.spring_ai_vector.service.filter;

import com.lily.spring_ai_vector.dto.LlmJudgeOutput;
import com.lily.spring_ai_vector.entity.LlmJudgeLog;
import com.lily.spring_ai_vector.repository.LlmJudgeLogRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * [L3] LLM 기반 최종 판정 서비스
 *
 * Structured Output: .entity(LlmJudgeOutput.class)로 JSON 자동 역직렬화
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

    public record LlmResult(
            String originalText,
            String normalized,
            LlmJudgeOutput judgeOutput,
            boolean isProfanity
    ) {}

    @Transactional
    public LlmResult check(String originalInput, String normalizedText) {

        if (!StringUtils.hasText(originalInput)) {
            return new LlmResult(originalInput, "", null, false);
        }

        log.info("[L3-LLM] 전달받은 정규화 텍스트 사용: '{}'", normalizedText);

        String promptUserText = "사용자 원문: " + originalInput + "\n정규화 버전: " + normalizedText;
        LlmJudgeOutput judgeOutput = llmChatClient.prompt()
                .system(sp -> sp.text(systemPromptResource).param("context", "없음"))
                .user(promptUserText)
                .call()
                .entity(LlmJudgeOutput.class);
        log.info("[L3-LLM] 분석 완료 | result={}, severity={}",
                judgeOutput.result(), judgeOutput.severity());

        boolean isProfanity = judgeOutput.result().isProfanity();

        // llm_judge_logs 저장 (isTrained=false → 관리자 검토 대기)
        LlmJudgeLog saved = llmJudgeLogRepository.save(LlmJudgeLog.builder()
                .originalText(originalInput)
                .normalizedText(normalizedText)
                .llmResult(judgeOutput.result())
                .llmReason(judgeOutput.reason())
                .category(judgeOutput.category())
                .severity(judgeOutput.severity())
                .isTrained(false)
                .build());
        log.info("[L3-LLM] 판정 로그 저장 완료 | id={}", saved.getId());

        return new LlmResult(originalInput, normalizedText, judgeOutput, isProfanity);
    }

    public LlmResult checkAsResult(String input) {
        String normalized = localFilterService.normalize(input);
        return check(input, normalized);
    }
}
