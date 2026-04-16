# 🌸 Spring AI Vector — 한국어 비속어 필터링 시스템

> **RAG 기반 벡터 검색 + LLM 순화**를 활용한 한국어 비속어 탐지 및 정제 API

<br>

## 프로젝트 소개

텍스트 내 비속어를 **벡터 유사도 검색(RAG)** 으로 탐지하고,  
**Ollama LLM** 을 통해 자연스러운 표현으로 순화하는 Spring Boot 백엔드 프로젝트입니다.

<br>

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.4 |
| AI | Spring AI 1.0.0-M5 |
| LLM | Ollama (llama3) |
| Embedding | bge-m3 |
| Vector DB | PostgreSQL + pgvector (HNSW / Cosine) |
| Build | Maven |

<br>

## 아키텍처

```
[Client]
   │
   ▼
[FilterController]
   ├── POST /api/admin/profanity/words   ← 단어 직접 등록
   ├── POST /api/admin/profanity/file    ← 파일 업로드 (.txt / .md / .json)
   ├── POST /api/filter/mask             ← RAG 기반 마스킹
   └── POST /api/filter/refine           ← LLM 기반 순화
         │
         ▼
[PgVector Store]  ←→  [Ollama Embedding: bge-m3]
         │
         ▼
  [Ollama LLM: llama3]
```

<br>

## API 명세

### 1. 비속어 등록 (관리자)

#### 단어 직접 입력
```
POST /api/admin/profanity/words
Content-Type: application/json
```
```json
{
  "words": ["비속어1", "비속어2"],
  "category": "PROFANITY",
  "severity": 2,
  "wordType": "WORD"
}
```

#### 파일 업로드
```
POST /api/admin/profanity/file
Content-Type: multipart/form-data

file     : [파일 선택] (.txt / .md / .json)
category : PROFANITY
severity : 2
```

---

### 2. 비속어 마스킹 (RAG)
```
POST /api/filter/mask
Content-Type: application/json
```
```json
{
  "text": "필터링할 문장을 입력하세요"
}
```

---

### 3. 비속어 순화 (LLM)
```
POST /api/filter/refine
Content-Type: application/json
```
```json
{
  "text": "순화할 문장을 입력하세요"
}
```

<br>

## 로컬 실행 방법

### 사전 준비
- **PostgreSQL** (port: 5433) + pgvector 확장 설치
- **Ollama** 실행 + 모델 다운로드

```bash
# Ollama 모델 다운로드
ollama pull llama3
ollama pull bge-m3
```

### DB 설정
```sql
-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;
```

`src/main/resources/schema.sql` 실행하여 테이블 생성

### 환경 설정

`application.yaml` 에서 DB 접속 정보 수정:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/postgres
    username: postgres
    password: your_password
```

### 실행

```bash
./mvnw spring-boot:run
```

<br>

## 프로젝트 구조

```
src/main/java/com/lily/spring_ai_vector/
├── config/
│   └── ChatClientConfig.java       # Ollama ChatClient 설정
├── controller/
│   └── FilterController.java       # REST API 엔드포인트
├── dto/
│   ├── FilterRequest.java
│   ├── MaskFilterResponse.java
│   ├── ProfanityUploadRequest.java
│   ├── ProfanityUploadResponse.java
│   └── RefineResponse.java
└── service/
    ├── ProfanityDataLoader.java     # 데이터 로딩
    ├── ProfanityFilterService.java  # RAG 마스킹 서비스
    ├── ProfanityRefineService.java  # LLM 순화 서비스
    └── ProfanityUploadService.java  # 비속어 업로드 서비스
```

<br>

## 보안 참고사항

- `application.yaml` 의 DB 패스워드는 환경변수로 분리 권장
- `/api/admin/**` 경로는 향후 Spring Security로 ROLE_ADMIN 인증 적용 예정

<br>

---

<div align="center">
  Made with 🌸 by <a href="https://github.com/lily-ds">lily</a>
</div>
