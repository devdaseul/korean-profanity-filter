package com.lily.spring_ai_vector.repository;

import com.lily.spring_ai_vector.entity.BadWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 비속어 블랙리스트 사전 리포지토리
 *
 * ▶ findAllActiveWords():
 *   - is_active = TRUE 인 단어만 조회 → 비활성화된 단어는 매칭 제외
 *   - BadWordFilter 가 앱 시작 시 이 목록을 메모리에 캐싱
 *
 * ▶ existsByWord():
 *   - 중복 체크용 (동일 단어 재적재 방지)
 */
public interface BadWordRepository extends JpaRepository<BadWord, Long> {

    /**
     * 활성화된 욕설 단어 목록만 문자열 리스트로 반환
     * JPQL 의 @Query 로 엔티티 전체 대신 word 컬럼만 조회 → 메모리 절약
     */
    @Query("SELECT b.word FROM BadWord b WHERE b.isActive = true")
    List<String> findAllActiveWords();

    /** 단어 존재 여부 확인 (중복 적재 방지) */
    boolean existsByWord(String word);
}
