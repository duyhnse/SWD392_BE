package swd392.group6.AIVES.grading.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Score of one rubric criterion within a thread, with max/weight snapshots (03 §1 decision 5). */
@Entity
@Table(name = "criterion_scores")
@Getter
@Setter
@NoArgsConstructor
public class CriterionScore {

    @Id
    @Column(name = "criterion_score_id", nullable = false, updatable = false)
    private UUID criterionScoreId;

    @Column(name = "question_grade_id", nullable = false, updatable = false)
    private UUID questionGradeId;

    @Column(name = "criterion_id", nullable = false, updatable = false)
    private UUID criterionId;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal maxScore;

    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal weightPercent;

    @Column(name = "ai_score", precision = 5, scale = 2)
    private BigDecimal aiScore;

    @Column(name = "ai_justification")
    private String aiJustification;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;
}
