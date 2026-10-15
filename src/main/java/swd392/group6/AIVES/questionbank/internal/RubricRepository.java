package swd392.group6.AIVES.questionbank.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RubricRepository extends JpaRepository<Rubric, UUID> {

    List<Rubric> findByCourseIdOrderByNameAsc(UUID courseId);

    @Query("select count(r) > 0 from Rubric r where r.courseId = :courseId and lower(r.name) = lower(:name) "
            + "and r.id <> :excludeId")
    boolean nameTaken(UUID courseId, String name, UUID excludeId);
}
