package swd392.group6.AIVES.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ExamTemplateRepository extends JpaRepository<ExamTemplate, UUID> {
}
