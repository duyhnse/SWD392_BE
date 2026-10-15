package swd392.group6.AIVES.exam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface BlueprintItemRepository extends JpaRepository<BlueprintItem, UUID> {

    List<BlueprintItem> findByVivaExamIdOrderBySortOrder(UUID vivaExamId);

    @Modifying(flushAutomatically = true)
    @Query("delete from BlueprintItem b where b.vivaExamId = :vivaExamId")
    void deleteByExam(UUID vivaExamId);
}
