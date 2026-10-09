package swd392.group6.AIVES.exam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import swd392.group6.AIVES.common.Language;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Đề thi — what is asked and how it is graded, without concrete questions (D45). {@code exam_templates}. */
@Entity
@Table(name = "exam_templates")
@Getter
@Setter
@NoArgsConstructor
class ExamTemplate {

    @Id
    @Column(name = "exam_template_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 30)
    private Language language;

    @Column(name = "max_followups_per_question", nullable = false)
    private int maxFollowupsPerQuestion;

    /** Longest single answer (one recording). */
    @Column(name = "max_answer_sec", nullable = false)
    private int maxAnswerSec;

    @Column(name = "silence_warning_sec", nullable = false)
    private int silenceWarningSec;

    @Column(name = "show_question_text", nullable = false)
    private boolean showQuestionText = true;

    /** 0–10, null = no pass mark. */
    @Column(name = "pass_score", precision = 4, scale = 2)
    private BigDecimal passScore;

    /** Grades every question of this template instead of the question's own rubric (rows may override again). */
    @Column(name = "rubric_id")
    private UUID rubricId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_pool_mode", nullable = false, length = 30)
    private QuestionPoolMode questionPoolMode = QuestionPoolMode.COURSE_BANK;

    /** Set when a buổi thi using it is published; a locked template can only be duplicated (D49). */
    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
