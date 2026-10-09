package swd392.group6.AIVES.questionbank.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findByCourseIdOrderByChapterNoAsc(UUID courseId);

    @Query("select count(t) > 0 from Chapter t where t.courseId = :courseId and lower(t.title) = lower(:title) "
            + "and t.id <> :excludeId")
    boolean titleTaken(UUID courseId, String title, UUID excludeId);

    @Query("select count(t) > 0 from Chapter t where t.courseId = :courseId and t.chapterNo = :chapterNo "
            + "and t.id <> :excludeId")
    boolean numberTaken(UUID courseId, int chapterNo, UUID excludeId);

    @Query("select coalesce(max(t.chapterNo), 0) from Chapter t where t.courseId = :courseId")
    int maxNumber(UUID courseId);
}
