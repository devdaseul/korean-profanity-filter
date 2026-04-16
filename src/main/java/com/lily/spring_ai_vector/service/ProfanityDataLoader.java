package com.lily.spring_ai_vector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 비속어 데이터 파일을 파싱하여 VectorDB에 적재하는 로더
 *
 * 지원 파일 형식:
 *  - *_aihub.json : AIHub 데이터셋 (sentences[].text + types + intensity 구조)
 *  - *.json       : 일반 JSON ({"content": "단어"} 구조)
 *  - *.md         : 마크다운 단어 목록 (줄 단위)
 *  - *.txt        : 텍스트 단어 목록 (줄 단위, | 구분자 지원)
 *
 * 메타데이터 (pgvector profanity_vectors 테이블 GENERATED 컬럼과 연동):
 *  - category   : 비속어 유형 (ABUSE / SEXUAL / HATE / CENSURE 등)
 *  - severity   : 심각도 1~3
 *  - source_type: 파일 유형 (TXT / MD / JSON / JSON_AIHUB)
 *  - word_type  : WORD(단어) / PHRASE(문장)
 *  - filename   : 원본 파일명
 *
 * 배치 전략:
 *  - BATCH_SIZE=100 으로 묶어 Ollama 임베딩 API 호출 최소화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfanityDataLoader {

    private static final int BATCH_SIZE = 100;
    private final VectorStore vectorStore;

    public void loadDataFromResource(Resource resource) {
        String fileName = resource.getFilename();
        if (fileName == null) return;

        String lower = fileName.toLowerCase();

        if (lower.contains("_aihub") && lower.endsWith(".json")) {
            loadAiHubJson(resource);
        } else if (lower.endsWith(".json")) {
            loadJson(resource);
        } else if (lower.endsWith(".txt")) {
            loadTxt(resource);
        } else if (lower.endsWith(".md")) {
            loadMd(resource);
        } else {
            log.info("[건너뜀] 지원하지 않는 형식: {}", fileName);
        }
    }

    // ── AIHub JSON (talksets-train-*_aihub.json) ───────────────
    /**
     * AIHub 데이터셋 전용 파서
     *
     * JSON 구조:
     *  [{"sentences": [{"text": "...", "types": ["ABUSE"], "intensity": 2.0, "is_immoral": true}]}]
     *
     * 매핑 규칙:
     *  - text        → content
     *  - types       → category (IMMORAL_NONE 제외한 첫 번째 타입)
     *  - intensity   → severity (0~1.9→1, 2.0~2.4→2, 2.5~3.0→3)
     *  - is_immoral=false 또는 types가 IMMORAL_NONE 뿐인 경우 → 저장 제외
     *  - source_type → JSON_AIHUB
     */
    private void loadAiHubJson(Resource resource) {
        String filename = resource.getFilename();
        List<Document> batch = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        int skipped = 0;

        try {
            JsonNode root = mapper.readTree(resource.getInputStream());
            for (JsonNode item : root) {
                JsonNode sentences = item.get("sentences");
                if (sentences == null) continue;

                for (JsonNode sentence : sentences) {
                    // is_immoral=false 제외
                    boolean isImmoral = sentence.path("is_immoral").asBoolean(false);
                    if (!isImmoral) { skipped++; continue; }

                    String text = sentence.path("text").asText("").trim();
                    if (text.isEmpty()) continue;

                    // types에서 IMMORAL_NONE 제외한 첫 번째 값을 category로
                    String category = "PROFANITY";
                    JsonNode typesNode = sentence.get("types");
                    if (typesNode != null) {
                        for (JsonNode t : typesNode) {
                            String type = t.asText();
                            if (!type.equals("IMMORAL_NONE")) {
                                category = type;
                                break;
                            }
                        }
                    }

                    // intensity → severity (0~1.9→1, 2.0~2.4→2, 2.5~3.0→3)
                    double intensity = sentence.path("intensity").asDouble(1.0);
                    String severity = intensityToSeverity(intensity);

                    Map<String, Object> meta = buildMeta(filename, category, severity, "JSON_AIHUB", "PHRASE");
                    batch.add(new Document(text, meta));

                    if (batch.size() >= BATCH_SIZE) {
                        vectorStore.add(new ArrayList<>(batch));
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) vectorStore.add(batch);
            log.info("[AIHub JSON 로드] {} → {}개 저장, {}개 제외(is_immoral=false)", filename, batch.size(), skipped);

        } catch (Exception e) {
            log.error("[AIHub JSON 로드 실패] {}: {}", filename, e.getMessage());
        }
    }

    /** intensity(0.0~3.0) → severity(1~3) 변환 */
    private String intensityToSeverity(double intensity) {
        if (intensity >= 2.5) return "3";
        if (intensity >= 2.0) return "2";
        return "1";
    }

    // ── JSON ──────────────────────────────────────────────────
    private void loadJson(Resource resource) {
        try {
            JsonReader jsonReader = new JsonReader(resource, "content");
            List<Document> raw = jsonReader.get();
            String filename = resource.getFilename();

            List<Document> docs = raw.stream()
                    .filter(d -> d.getText() != null && !d.getText().isBlank())
                    .map(d -> new Document(d.getText().trim(),
                            buildMeta(filename, inferCategory(filename),
                                    inferSeverity(filename), "JSON",
                                    d.getText().trim().length() <= 5 ? "WORD" : "PHRASE")))
                    .collect(Collectors.toList());

            saveBatch(docs);
            log.info("[JSON 로드] {} → {}개", filename, docs.size());
        } catch (Exception e) {
            log.error("[JSON 로드 실패] {}: {}", resource.getFilename(), e.getMessage());
        }
    }

    // ── TXT ──────────────────────────────────────────────────
    private void loadTxt(Resource resource) {
        String filename = resource.getFilename();
        List<Document> batch = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String cleaned = line.replace("{", "").replace("}", "").trim();
                if (cleaned.isEmpty()) continue;

                for (String part : cleaned.split("\\|")) {
                    String word = part.trim();
                    if (word.isEmpty()) continue;

                    String wordType = word.length() <= 5 ? "WORD" : "PHRASE";
                    batch.add(new Document(word,
                            buildMeta(filename, inferCategory(filename),
                                    inferSeverity(filename), "TXT", wordType)));

                    if (batch.size() >= BATCH_SIZE) {
                        vectorStore.add(new ArrayList<>(batch));
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) vectorStore.add(batch);
            log.info("[TXT 로드] {} 완료", filename);

        } catch (IOException e) {
            log.error("[TXT 로드 실패] {}: {}", filename, e.getMessage());
        }
    }

    // ── MD ───────────────────────────────────────────────────
    private void loadMd(Resource resource) {
        String filename = resource.getFilename();
        try {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            List<Document> docs = Arrays.stream(content.split("\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .filter(line -> !line.startsWith("---"))
                    .filter(line -> !line.startsWith("```"))
                    .filter(line -> !line.startsWith("|"))
                    .map(line -> new Document(line,
                            buildMeta(filename, inferCategory(filename),
                                    inferSeverity(filename), "MD",
                                    line.length() <= 5 ? "WORD" : "PHRASE")))
                    .collect(Collectors.toList());

            saveBatch(docs);
            log.info("[MD 로드] {} → {}개", filename, docs.size());

        } catch (IOException e) {
            log.error("[MD 로드 실패] {}: {}", filename, e.getMessage());
        }
    }

    // ── 공통 유틸 ─────────────────────────────────────────────

    private void saveBatch(List<Document> docs) {
        for (int i = 0; i < docs.size(); i += BATCH_SIZE) {
            vectorStore.add(new ArrayList<>(docs.subList(i, Math.min(i + BATCH_SIZE, docs.size()))));
        }
    }

    private Map<String, Object> buildMeta(
            String filename, String category, String severity,
            String sourceType, String wordType) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", filename);
        meta.put("category", category);
        meta.put("severity", severity);
        meta.put("source_type", sourceType);
        meta.put("word_type", wordType);
        return meta;
    }

    private String inferCategory(String filename) {
        String lower = filename.toLowerCase();
        if (lower.contains("hate")) return "HATE_SPEECH";
        if (lower.contains("sexual") || lower.contains("adult")) return "SEXUAL";
        return "PROFANITY";
    }

    private String inferSeverity(String filename) {
        String lower = filename.toLowerCase();
        if (lower.contains("hate")) return "2";
        if (lower.contains("talkset")) return "1";
        return "1";
    }
}
