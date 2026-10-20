package swd392.group6.AIVES.questionbank.repository;

import swd392.group6.AIVES.questionbank.domain.Topic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    List<Topic> findByCourseIdOrderBySortOrderAscNameAsc(UUID courseId);

    @Query("select count(t) > 0 from Topic t where t.courseId = :courseId and lower(t.name) = lower(:name) "
            + "and t.id <> :excludeId")
    boolean nameTaken(UUID courseId, String name, UUID excludeId);
}
