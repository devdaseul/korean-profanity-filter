package com.lily.spring_ai_vector.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

/**
 * 비속어 블랙리스트 사전 엔티티
 *
 * ▶ bad_word 테이블과 1:1 매핑
 *
 * ▶ is_active 설계 의도:
 *   - 욕설 단어를 DELETE 하지 않고 비활성화(FALSE)로 관리
 *   - "구리다"처럼 욕설과 혼용되는 정상어를 나중에 제외할 때 사용
 *   - 이력(언제 비활성화했는지) 추적 가능
 *
 * ▶ @Column(unique = true):
 *   - DB UNIQUE 제약 + JPA 레벨에서도 중복 삽입 방지
 */
@Entity
@Table(name = "bad_word")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BadWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 욕설 원문 (UNIQUE) */
    @Column(nullable = false, unique = true, length = 200)
    private String word;

    /** 욕설 유형: ABUSE / SEXUAL / HATE / CENSURE / VIOLENCE 등 */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String category = "ABUSE";

    /** 출처 파일명 (korean-bad-words.md 등) */
    @Column(length = 200)
    private String source;

    /** 활성화 여부. FALSE = 화이트리스트 처리됨 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
