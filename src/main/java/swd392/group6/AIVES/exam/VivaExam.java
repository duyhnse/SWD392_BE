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

import java.time.Instant;
import java.util.UUID;

/** Buổi thi — a đề thi + check-in window + roster + connection rules (D47). {@code viva_exams}. */
@Entity
@Table(name = "viva_exams")
@Getter
@Setter
@NoArgsConstructor
class VivaExam {

    @Id
    @Column(name = "viva_exam_id", nullable = false, updatable = false)
    private UUID id;

    /** Short number shown as a code (D56); assigned by the database sequence on insert. */
    @org.hibernate.annotations.Generated
    @Column(name = "display_no", insertable = false, updatable = false)
    private Long displayNo;

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

    @Column(name = "exam_template_id", nullable = false)
    private UUID templateId;

    /** Students may start (check in) from … */
    @Column(name = "checkin_opens_at", nullable = false)
    private Instant checkinOpensAt;

    /** … until here. There is no fixed end: every attempt ends at its own deadline (D47). */
    @Column(name = "checkin_closes_at", nullable = false)
    private Instant checkinClosesAt;

    /** Offline longer than this → the attempt is INTERRUPTED (D51). */
    @Column(name = "reconnect_grace_sec", nullable = false)
    private int reconnectGraceSec = 60;

    @Column(name = "max_disconnects", nullable = false)
    private int maxDisconnects = 3;

    /** Total offline (frozen) time allowed per attempt. */
    @Column(name = "max_frozen_sec", nullable = false)
    private int maxFrozenSec = 180;

    /** Offline longer than this during a main question → the question is replaced when the pool allows. */
    @Column(name = "replace_main_after_sec", nullable = false)
    private int replaceMainAfterSec = 20;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExamStatus status = ExamStatus.DRAFT;

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
