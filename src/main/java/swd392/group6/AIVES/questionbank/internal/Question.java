package swd392.group6.AIVES.questionbank.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Main viva question of a course (WF1). {@code ai_suggested_rubric} (jsonb) is deliberately not mapped:
 * it is written only by the AI generation milestone and read with SQL.
 */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "reference_answer")
    private String referenceAnswer;

    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", length = 30)
    private BloomLevel bloomLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 30)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private QuestionStatus status = QuestionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 30)
    private QuestionOrigin origin = QuestionOrigin.MANUAL;

    @Column(name = "rubric_id")
    private UUID rubricId;

    @Column(name = "ai_original_content")
    private String aiOriginalContent;

    @Column(name = "ai_original_reference_answer")
    private String aiOriginalReferenceAnswer;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_suggested_bloom", length = 30)
    private BloomLevel aiSuggestedBloom;

    @Column(name = "generation_request_id")
    private UUID generationRequestId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "discarded_at")
    private Instant discardedAt;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "supersedes_question_id")
    private UUID supersedesQuestionId;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
