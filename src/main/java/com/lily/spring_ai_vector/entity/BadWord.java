package com.lily.spring_ai_vector.entity;

import com.lily.spring_ai_vector.enums.Category;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

/**
 * 비속어 블랙리스트 사전 엔티티 (bad_words 테이블)
 *
 * isActive: DELETE 대신 비활성화로 관리 — 정상어와 혼용되는 단어 화이트리스트 처리에 활용
 */
@Entity
@Table(name = "bad_words")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BadWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String word;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Category category = Category.ABUSE;

    @Column(length = 200)
    private String source;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
