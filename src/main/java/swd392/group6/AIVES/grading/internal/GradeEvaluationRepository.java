package swd392.group6.AIVES.grading.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradeEvaluationRepository extends JpaRepository<GradeEvaluation, UUID> {

    Optional<GradeEvaluation> findBySessionId(UUID sessionId);

    List<GradeEvaluation> findBySessionIdIn(Collection<UUID> sessionIds);
}
