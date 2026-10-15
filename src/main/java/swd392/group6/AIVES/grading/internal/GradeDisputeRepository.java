package swd392.group6.AIVES.grading.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GradeDisputeRepository extends JpaRepository<GradeDispute, UUID> {

    boolean existsByEvaluationIdAndStatus(UUID evaluationId, DisputeStatus status);

    List<GradeDispute> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<GradeDispute> findByEvaluationIdInOrderByCreatedAtDesc(Collection<UUID> evaluationIds);
}
