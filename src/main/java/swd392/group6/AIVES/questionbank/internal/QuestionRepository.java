package swd392.group6.AIVES.questionbank.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID>, JpaSpecificationExecutor<Question> {

    boolean existsByTopicId(UUID topicId);

    boolean existsByRubricId(UUID rubricId);

    long countByRubricId(UUID rubricId);

    @Query("select q.topicId, count(q) from Question q where q.courseId = :courseId group by q.topicId")
    List<Object[]> countByTopic(UUID courseId);

    @Query("select q.rubricId, count(q) from Question q where q.rubricId in :rubricIds group by q.rubricId")
    List<Object[]> countByRubric(Collection<UUID> rubricIds);

    @Query("select count(q) > 0 from Question q where q.supersedesQuestionId = :id and q.status <> "
            + "swd392.group6.AIVES.questionbank.internal.QuestionStatus.DISCARDED")
    boolean hasActiveSuccessor(UUID id);
}
