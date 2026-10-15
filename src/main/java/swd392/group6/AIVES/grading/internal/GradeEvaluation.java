package swd392.group6.AIVES.grading.internal;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Bảng chấm of one lượt thi ({@code grade_evaluations}). */
@Entity
@Table(name = "grade_evaluations")
@Getter
@Setter
@NoArgsConstructor
public class GradeEvaluation {

    @Id
    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "attempt_id", nullable = false, updatable = false, unique = true)
    private UUID attemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EvaluationStatus status;

    @Column(name = "ai_total_score", precision = 5, scale = 2)
    private BigDecimal aiTotalScore;

    @Column(name = "final_total_score", precision = 5, scale = 2)
    private BigDecimal finalTotalScore;

    @Column(name = "ai_general_feedback")
    private String aiGeneralFeedback;

    @Column(name = "lecturer_comment")
    private String lecturerComment;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "llm_model", length = 100)
    private String llmModel;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
