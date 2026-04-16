package com.lily.spring_ai_vector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lily.spring_ai_vector.dto.ProfanityUploadRequest;
import com.lily.spring_ai_vector.dto.ProfanityUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * [API 1] 비속어 데이터 업로드 서비스
 *
 * 역할:
 *  - 관리자가 Postman 등을 통해 전송한 비속어 데이터를
 *    임베딩하여 pgvector 에 저장하는 진입점
 *
 * 지원 입력 방식:
 *  1. JSON Body (단어/문장 리스트 직접 입력)
 *  2. MultipartFile - .txt (줄 단위 또는 | 구분)
 *  3. MultipartFile - .md  (마크다운 줄 단위)
 *  4. MultipartFile - .json (Spring AI JsonReader 활용)
 *
 * 설계 핵심:
 *  - 모든 Document 에 category / severity / source_type / word_type 메타데이터를 부여
 *  - 메타데이터는 vectorDB 저장 후 RAG 검색 시 pre-filter 조건으로 사용
 *  - 배치 크기 100으로 bulk insert → 임베딩 API 호출 수 최소화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfanityUploadService {

    private static final int BATCH_SIZE = 100;

    private final VectorStore vectorStore;

    // ──────────────────────────────────────────
    //  [1] JSON Body 직접 입력
    // ──────────────────────────────────────────

    /**
     * Postman Body 에 단어 리스트를 직접 입력하는 경우
     *
     * 왜 이 방식?
     *  - 관리자가 소량의 단어를 빠르게 추가할 때 파일 없이 즉시 등록 가능
     *  - category/severity/wordType 을 요청마다 다르게 지정해 세밀하게 분류 가능
     */
    public ProfanityUploadResponse uploadFromRequest(ProfanityUploadRequest request) {
        if (request.words() == null || request.words().isEmpty()) {
            return new ProfanityUploadResponse(0, "저장할 단어가 없습니다.", "API");
        }

        List<Document> docs = request.words().stream()
                .filter(w -> w != null && !w.isBlank())
                .map(word -> new Document(word.trim(), buildMeta(
                        request.category(),
                        String.valueOf(request.severity()),
                        "API",
                        request.wordType(),
                        "direct-api"
                )))
                .collect(Collectors.toList());

        saveBatch(docs);
        log.info("[업로드-API] {}개 저장 완료 (category={}, severity={})",
                docs.size(), request.category(), request.severity());

        return new ProfanityUploadResponse(docs.size(),
                docs.size() + "개의 단어/문장이 저장되었습니다.", "API");
    }

    // ──────────────────────────────────────────
    //  [2] 파일 업로드 (Multipart)
    // ──────────────────────────────────────────

    /**
     * MultipartFile 을 받아 확장자에 따라 파서를 분기
     *
     * 왜 이 방식?
     *  - 대용량 비속어 사전 파일(수천~수만 줄)을 한 번에 업로드 가능
     *  - txt / md / json 등 다양한 포맷을 하나의 엔드포인트로 처리
     *  - category, severity 는 요청 파라미터로 받아 유연하게 지정
     */
    public ProfanityUploadResponse uploadFromFile(
            MultipartFile file,
            String category,
            int severity
    ) throws IOException {

        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown");
        String lower = originalFilename.toLowerCase();
        String resolvedCategory = (category != null && !category.isBlank()) ? category : inferCategory(originalFilename);

        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return originalFilename;
            }
        };

        List<Document> docs;
        String sourceType;

        if (lower.contains("_aihub") && lower.endsWith(".json")) {
            docs = parseAiHubJson(resource, originalFilename);
            sourceType = "JSON_AIHUB";
        } else if (lower.endsWith(".json")) {
            docs = parseJson(resource, resolvedCategory, severity, originalFilename);
            sourceType = "JSON";
        } else if (lower.endsWith(".md")) {
            docs = parseMd(resource, resolvedCategory, severity, originalFilename);
            sourceType = "MD";
        } else if (lower.endsWith(".txt")) {
            docs = parseTxt(resource, resolvedCategory, severity, originalFilename);
            sourceType = "TXT";
        } else {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + originalFilename);
        }

        saveBatch(docs);
        log.info("[업로드-파일] {} → {}개 저장 완료 (category={}, severity={})",
                originalFilename, docs.size(), resolvedCategory, severity);

        return new ProfanityUploadResponse(docs.size(),
                originalFilename + " 파일에서 " + docs.size() + "개가 저장되었습니다.", sourceType);
    }

    // ──────────────────────────────────────────
    //  내부 파서
    // ──────────────────────────────────────────

    /**
     * AIHub 데이터셋 JSON 파서 (talksets-train-*_aihub.json)
     *
     * - is_immoral=false 제외
     * - types에서 IMMORAL_NONE 제외한 첫 번째 타입 → category
     * - intensity(0~3.0) → severity(1~3)
     * - category/severity 요청 파라미터 무시 (JSON 내부값 우선)
     */
    private List<Document> parseAiHubJson(Resource resource, String filename) {
        List<Document> docs = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        int skipped = 0;

        try {
            JsonNode root = mapper.readTree(resource.getInputStream());
            for (JsonNode item : root) {
                JsonNode sentences = item.get("sentences");
                if (sentences == null) continue;

                for (JsonNode sentence : sentences) {
                    boolean isImmoral = sentence.path("is_immoral").asBoolean(false);
                    if (!isImmoral) { skipped++; continue; }

                    String text = sentence.path("text").asText("").trim();
                    if (text.isEmpty()) continue;

                    // types → category (IMMORAL_NONE 제외)
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

                    // intensity → severity
                    double intensity = sentence.path("intensity").asDouble(1.0);
                    String severity = intensity >= 2.5 ? "3" : intensity >= 2.0 ? "2" : "1";

                    docs.add(new Document(text,
                            buildMeta(category, severity, "JSON_AIHUB", "PHRASE", filename)));
                }
            }
            log.info("[AIHub JSON 파싱] {} → {}개 저장 대상, {}개 제외(is_immoral=false)",
                    filename, docs.size(), skipped);

        } catch (Exception e) {
            log.error("[AIHub JSON 파싱 실패] {}: {}", filename, e.getMessage());
        }
        return docs;
    }

    /**
     * 일반 JSON 파싱 (content 키 기반)
     */
    private List<Document> parseJson(Resource resource, String category, int severity, String filename) {
        try {
            JsonReader jsonReader = new JsonReader(resource, "content");
            List<Document> raw = jsonReader.get();
            return raw.stream()
                    .filter(d -> d.getText() != null && !d.getText().isBlank())
                    .map(d -> {
                        Map<String, Object> meta = buildMeta(category, String.valueOf(severity), "JSON", "WORD", filename);
                        return new Document(d.getText().trim(), meta);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[JSON 파싱 실패] {}: {}", filename, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * TXT 파일 파싱
     *
     * 처리 규칙:
     *  - 빈 줄 건너뜀
     *  - '{', '}' 제거 (일부 데이터셋 형식 대응)
     *  - '|' 구분자로 다중 표현 지원 (talksets 형식)
     *
     * 왜 이렇게?
     *  - talksets-train-*.txt 는 "{발화자|발화내용}" 형태로 구성
     *  - korean-bad-words 계열은 줄 단위 단어
     *  - 두 형식을 하나의 파서로 처리하기 위해 | split 적용
     */
    private List<Document> parseTxt(Resource resource, String category, int severity, String filename) {
        List<Document> docs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String cleaned = line.replace("{", "").replace("}", "").trim();
                if (cleaned.isEmpty()) continue;

                // | 구분자 처리 (talksets 형식 대응)
                String[] parts = cleaned.split("\\|");
                for (String part : parts) {
                    String word = part.trim();
                    if (!word.isEmpty()) {
                        // 문장 길이에 따라 word_type 자동 분류
                        String wordType = word.length() <= 5 ? "WORD" : "PHRASE";
                        docs.add(new Document(word, buildMeta(category, String.valueOf(severity), "TXT", wordType, filename)));
                    }
                }
            }
        } catch (IOException e) {
            log.error("[TXT 파싱 실패] {}: {}", filename, e.getMessage());
        }
        return docs;
    }

    /**
     * MD(마크다운) 파일 파싱
     *
     * 처리 규칙:
     *  - '#' 으로 시작하는 제목 줄 건너뜀
     *  - '---', '```', '|' 테이블 구분선 건너뜀
     *  - 순수 텍스트 줄만 Document 로 변환
     *
     * 왜 이렇게?
     *  - korean-bad-words.md 는 단어 목록이 줄 단위로 나열된 구조
     *  - 마크다운 메타 문법을 제거해 깨끗한 텍스트만 임베딩
     */
    private List<Document> parseMd(Resource resource, String category, int severity, String filename) {
        List<Document> docs = new ArrayList<>();
        try {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Arrays.stream(content.split("\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .filter(line -> !line.startsWith("---"))
                    .filter(line -> !line.startsWith("```"))
                    .filter(line -> !line.startsWith("|"))
                    .forEach(line -> {
                        String wordType = line.length() <= 5 ? "WORD" : "PHRASE";
                        docs.add(new Document(line, buildMeta(category, String.valueOf(severity), "MD", wordType, filename)));
                    });
        } catch (IOException e) {
            log.error("[MD 파싱 실패] {}: {}", filename, e.getMessage());
        }
        return docs;
    }

    // ──────────────────────────────────────────
    //  공통 유틸
    // ──────────────────────────────────────────

    /**
     * 배치 단위로 VectorStore 에 저장
     *
     * 왜 배치?
     *  - 임베딩 모델(Ollama) 에 단건씩 요청하면 네트워크 오버헤드가 매우 큼
     *  - BATCH_SIZE=100 으로 묶어 한 번에 전송 → 처리 속도 수배 향상
     */
    private void saveBatch(List<Document> docs) {
        for (int i = 0; i < docs.size(); i += BATCH_SIZE) {
            List<Document> batch = docs.subList(i, Math.min(i + BATCH_SIZE, docs.size()));
            vectorStore.add(new ArrayList<>(batch));
            log.debug("[배치 저장] {}~{}번째 문서 저장", i + 1, i + batch.size());
        }
    }

    /**
     * VectorStore 메타데이터 Map 빌더
     *
     * 왜 이 필드들?
     *  - category: RAG 검색 시 WHERE category = 'PROFANITY' pre-filter 적용
     *  - severity: 마스킹 강도 분기 (1→*, 2→**, 3→***)
     *  - source_type: 파일 유형별 재처리·삭제 추적
     *  - word_type: WORD/PHRASE/REGEX 검색 전략 분기
     *  - filename: 업로드 파일 추적·감사 로그
     */
    private Map<String, Object> buildMeta(
            String category,
            String severity,
            String sourceType,
            String wordType,
            String filename
    ) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("category", category);
        meta.put("severity", severity);
        meta.put("source_type", sourceType);
        meta.put("word_type", wordType);
        meta.put("filename", filename);
        return meta;
    }

    /**
     * 파일명으로 카테고리 추론
     *
     * 왜 자동 추론?
     *  - 관리자가 category 파라미터를 생략했을 때 파일명 컨벤션으로 자동 분류
     *  - hate → HATE_SPEECH, sexual / adult → SEXUAL, 그 외 → PROFANITY
     */
    private String inferCategory(String filename) {
        String lower = filename.toLowerCase();
        if (lower.contains("hate")) return "HATE_SPEECH";
        if (lower.contains("sexual") || lower.contains("adult")) return "SEXUAL";
        return "PROFANITY";
    }
}
