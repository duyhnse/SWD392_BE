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

import java.time.Instant;
import java.util.UUID;

/** Phúc khảo: a student's appeal of a confirmed, released evaluation (FG5). */
@Entity
@Table(name = "grade_disputes")
@Getter
@Setter
@NoArgsConstructor
public class GradeDispute {

    @Id
    @Column(name = "dispute_id", nullable = false, updatable = false)
    private UUID disputeId;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    /** JSON array of question_grade ids the student contests (null = the whole evaluation). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_grade_ids", updatable = false)
    private String questionGradeIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DisputeStatus status;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
