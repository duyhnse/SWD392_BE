package swd392.group6.AIVES.grading.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CriterionScoreRepository extends JpaRepository<CriterionScore, UUID> {

    List<CriterionScore> findByQuestionGradeIdIn(Collection<UUID> questionGradeIds);
}
