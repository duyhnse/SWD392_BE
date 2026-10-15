package swd392.group6.AIVES.grading.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuestionGradeRepository extends JpaRepository<QuestionGrade, UUID> {

    List<QuestionGrade> findByEvaluationId(UUID evaluationId);

    List<QuestionGrade> findByEvaluationIdIn(Collection<UUID> evaluationIds);
}
