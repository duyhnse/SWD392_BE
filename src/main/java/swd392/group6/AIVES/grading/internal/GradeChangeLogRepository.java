package swd392.group6.AIVES.grading.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GradeChangeLogRepository extends JpaRepository<GradeChangeLog, UUID> {

    List<GradeChangeLog> findByEvaluationIdOrderByChangedAtAscChangeIdAsc(UUID evaluationId);
}
