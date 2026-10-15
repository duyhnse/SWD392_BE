package swd392.group6.AIVES.questionbank.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, UUID> {

    List<CourseMaterial> findByCourseIdOrderByCreatedAtDesc(UUID courseId);
}
