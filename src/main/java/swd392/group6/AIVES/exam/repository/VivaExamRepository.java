package swd392.group6.AIVES.exam.repository;

import swd392.group6.AIVES.exam.domain.VivaExam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VivaExamRepository extends JpaRepository<VivaExam, UUID> {
}
