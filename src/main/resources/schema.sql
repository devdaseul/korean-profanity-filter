-- ========================================================
--  pgvector 기반 비속어 필터링 Vector DB 스키마
--  테이블명: spring_ai_data_bge
--  모델: bge-m3 (dimensions: 1024)
-- ========================================================

-- pgvector 확장 활성화 (최초 1회만 실행)
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- --------------------------------------------------------
--  메인 벡터 저장 테이블
-- --------------------------------------------------------
-- [설계 근거]
--  1. id: UUID → 분산 환경·샤딩 대비, 충돌 없는 PK
--  2. content: 실제 임베딩된 텍스트 원문 (단어/문장 모두 저장)
--  3. embedding: 1024차원 벡터 (bge-m3 출력 차원)
--  4. metadata: JSONB → Spring AI 가 자동으로 읽고 쓰는 표준 컬럼.
--               추가 필드를 스키마 변경 없이 확장 가능
--  5. category: GENERATED COLUMN으로 metadata 에서 추출 →
--               메타데이터와 카테고리 컬럼이 항상 동기화됨
--  6. severity:  1(경미)~3(심각) → 마스킹 강도·순화 방식 제어에 활용
--  7. source_type: 파일 유형 분류 → 입력 경로 추적·재처리 용이
--  8. word_type:  WORD / PHRASE / REGEX → 검색 전략 분기에 활용
--  9. created_at: 감사 로그·TTL 정책에 활용
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS profanity_vectors (
    -- PK: UUID (Spring AI 기본 규격)
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- 임베딩 원문 텍스트
    content     TEXT        NOT NULL,

    -- 1024차원 임베딩 벡터 (bge-m3)
    embedding   VECTOR(1024),

    -- Spring AI 가 직렬화하여 관리하는 메타데이터 (JSONB)
    -- 예) {"filename":"korean-bad-words.md","category":"PROFANITY","severity":"2","source_type":"MD","word_type":"WORD"}
    metadata    JSONB       NOT NULL DEFAULT '{}',

    -- metadata 에서 자동 추출되는 카테고리 (항상 동기화)
    -- Spring AI 쿼리 필터를 이 컬럼으로 걸면 빠른 pre-filter 가능
    category    TEXT        GENERATED ALWAYS AS (metadata->>'category') STORED,

    -- 심각도: 1=경미(은어), 2=일반 비속어, 3=혐오·성적 표현
    severity    SMALLINT    GENERATED ALWAYS AS (
                    CASE
                        WHEN (metadata->>'severity')::int IS NOT NULL
                        THEN (metadata->>'severity')::int
                        ELSE 1
                    END
                ) STORED,

    -- 출처 파일 유형: TXT / MD / JSON / API
    source_type TEXT        GENERATED ALWAYS AS (metadata->>'source_type') STORED,

    -- 단어 유형: WORD(단일 단어), PHRASE(문장), REGEX(정규식 패턴)
    word_type   TEXT        GENERATED ALWAYS AS (metadata->>'word_type') STORED,

    -- 저장 시각 (감사 로그)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------
--  인덱스 설계
-- --------------------------------------------------------

-- [1] HNSW 벡터 인덱스 (코사인 유사도 기반 ANN 검색)
--     - IVFFlat 대비 검색 속도 2~5배 빠름
--     - m=16, ef_construction=64 : 정확도/속도 균형
CREATE INDEX IF NOT EXISTS idx_pv_embedding_hnsw
    ON profanity_vectors
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- [2] category 필터용 B-Tree 인덱스
--     - WHERE category = 'PROFANITY' 처럼 pre-filter 할 때 사용
CREATE INDEX IF NOT EXISTS idx_pv_category
    ON profanity_vectors (category);

-- [3] severity 필터용 인덱스
--     - 심각도 높은 표현만 먼저 검색할 때 사용
CREATE INDEX IF NOT EXISTS idx_pv_severity
    ON profanity_vectors (severity);

-- [4] source_type 인덱스
--     - 파일 유형별 재처리·삭제 시 사용
CREATE INDEX IF NOT EXISTS idx_pv_source_type
    ON profanity_vectors (source_type);

-- [5] metadata JSONB GIN 인덱스
--     - metadata @> '{"filename":"xxx"}' 같은 JSONB 포함 쿼리 가속
CREATE INDEX IF NOT EXISTS idx_pv_metadata_gin
    ON profanity_vectors USING gin (metadata);

-- [6] 생성일 인덱스 (최신 순 정렬·범위 검색)
CREATE INDEX IF NOT EXISTS idx_pv_created_at
    ON profanity_vectors (created_at DESC);

-- --------------------------------------------------------
--  편의 뷰: 카테고리별 단어 수 확인
-- --------------------------------------------------------
CREATE OR REPLACE VIEW vw_profanity_stats AS
SELECT
    category,
    source_type,
    word_type,
    severity,
    COUNT(*) AS word_count,
    MAX(created_at) AS last_uploaded_at
FROM profanity_vectors
GROUP BY category, source_type, word_type, severity
ORDER BY category, severity DESC;
