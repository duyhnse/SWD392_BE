package swd392.group6.AIVES.grading.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Grade of one thread (main question + follow-ups) — the grading unit (06 §3). JSON columns are kept as raw
 * JSON text and (de)serialised by {@link GradingJson}.
 */
@Entity
@Table(name = "question_grades")
@Getter
@Setter
@NoArgsConstructor
public class QuestionGrade {

    @Id
    @Column(name = "question_grade_id", nullable = false, updatable = false)
    private UUID questionGradeId;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "session_question_id", nullable = false, updatable = false)
    private UUID sessionQuestionId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_snapshot", nullable = false, updatable = false)
    private String rubricSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private QuestionGradeStatus status;

    @Column(name = "ai_score", precision = 5, scale = 2)
    private BigDecimal aiScore;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "include_in_total", nullable = false)
    private boolean includeInTotal = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_strengths")
    private String aiStrengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_weaknesses")
    private String aiWeaknesses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_missing_points")
    private String aiMissingPoints;

    @Column(name = "ai_feedback")
    private String aiFeedback;

    @Column(name = "ai_error")
    private String aiError;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signals")
    private String signals;

    @Column(name = "lecturer_comment")
    private String lecturerComment;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
