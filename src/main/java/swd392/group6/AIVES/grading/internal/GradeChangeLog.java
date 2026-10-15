package swd392.group6.AIVES.grading.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Audit row for every lecturer change of a grade (BR-G7). Append-only. */
@Entity
@Table(name = "grade_change_log")
@Getter
@Setter
@NoArgsConstructor
public class GradeChangeLog {

    @Id
    @Column(name = "change_id", nullable = false, updatable = false)
    private UUID changeId;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "question_grade_id", updatable = false)
    private UUID questionGradeId;

    @Column(name = "criterion_score_id", updatable = false)
    private UUID criterionScoreId;

    @Column(name = "field", nullable = false, length = 40, updatable = false)
    private String field;

    @Column(name = "old_value", updatable = false)
    private String oldValue;

    @Column(name = "new_value", updatable = false)
    private String newValue;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
