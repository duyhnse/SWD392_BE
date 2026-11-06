package swd392.group6.AIVES.exam.repository;

import swd392.group6.AIVES.exam.domain.ExamTemplateItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ExamTemplateItemRepository extends JpaRepository<ExamTemplateItem, UUID> {

    List<ExamTemplateItem> findByTemplateIdOrderBySortOrder(UUID templateId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ExamTemplateItem i where i.templateId = :templateId")
    void deleteByTemplate(UUID templateId);
}
