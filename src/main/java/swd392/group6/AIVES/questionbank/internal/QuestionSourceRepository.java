package swd392.group6.AIVES.questionbank.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface QuestionSourceRepository extends JpaRepository<QuestionSource, UUID> {

    List<QuestionSource> findByQuestionId(UUID questionId);

    boolean existsByMaterialId(UUID materialId);

    @Modifying(flushAutomatically = true)
    @Query("delete from QuestionSource s where s.questionId = :questionId")
    void deleteByQuestion(UUID questionId);
}
