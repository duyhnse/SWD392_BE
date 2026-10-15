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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Buổi thi — {@code viva_exams} (V1 + V2). */
@Entity
@Table(name = "viva_exams")
@Getter
@Setter
@NoArgsConstructor
class VivaExam {

    @Id
    @Column(name = "viva_exam_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "instructions")
    private String instructions;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "examiner_id", nullable = false)
    private UUID examinerId;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 30)
    private Language language;

    @Column(name = "main_question_count", nullable = false)
    private int mainQuestionCount;

    @Column(name = "max_followups_per_question", nullable = false)
    private int maxFollowupsPerQuestion;

    @Column(name = "time_limit_per_student_sec", nullable = false)
    private int timeLimitPerStudentSec;

    @Column(name = "answer_time_limit_sec", nullable = false)
    private int answerTimeLimitSec;

    @Column(name = "silence_warning_sec", nullable = false)
    private int silenceWarningSec;

    @Column(name = "reconnect_grace_sec", nullable = false)
    private int reconnectGraceSec;

    /** Legacy filter, null = all topics. Ignored when a blueprint exists. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topic_ids")
    private List<UUID> topicIds;

    /** Legacy filter, null = all levels. Ignored when a blueprint exists. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bloom_levels")
    private List<BloomLevel> bloomLevels;

    @Column(name = "selection_strategy", nullable = false, length = 30)
    private String selectionStrategy = "RANDOM_BALANCED";

    @Column(name = "show_question_text", nullable = false)
    private boolean showQuestionText = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExamStatus status = ExamStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_pool_mode", nullable = false, length = 30)
    private QuestionPoolMode questionPoolMode = QuestionPoolMode.COURSE_BANK;

    @Column(name = "results_released", nullable = false)
    private boolean resultsReleased;

    @Column(name = "results_released_at")
    private Instant resultsReleasedAt;

    @Column(name = "retake_of_viva_exam_id", updatable = false)
    private UUID retakeOfVivaExamId;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
