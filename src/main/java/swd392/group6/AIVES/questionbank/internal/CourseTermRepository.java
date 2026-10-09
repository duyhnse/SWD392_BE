package swd392.group6.AIVES.questionbank.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CourseTermRepository extends JpaRepository<CourseTerm, UUID> {

    @Query("select t from CourseTerm t where t.courseId = :courseId order by lower(t.term)")
    List<CourseTerm> findByCourse(UUID courseId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CourseTerm t where t.courseId = :courseId")
    void deleteByCourse(UUID courseId);
}
