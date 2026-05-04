package com.lily.spring_ai_vector.repository;

import com.lily.spring_ai_vector.entity.BadWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BadWordRepository extends JpaRepository<BadWord, Long> {

    /** isActive = true 인 단어만 조회 (word 컬럼만 반환) */
    @Query("SELECT b.word FROM BadWord b WHERE b.isActive = true")
    List<String> findAllActiveWords();

    boolean existsByWord(String word);
}
