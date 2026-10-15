package swd392.group6.AIVES.exam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import swd392.group6.AIVES.questionbank.BloomLevel;

import java.util.UUID;

/** One row of the cấu trúc đề — {@code viva_exam_blueprint_items} (D29). */
@Entity
@Table(name = "viva_exam_blueprint_items")
@Getter
@NoArgsConstructor
@AllArgsConstructor
class BlueprintItem {

    @Id
    @Column(name = "blueprint_item_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "viva_exam_id", nullable = false, updatable = false)
    private UUID vivaExamId;

    /** null = any topic of the course. */
    @Column(name = "topic_id")
    private UUID topicId;

    /** null = any Bloom level. */
    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", length = 30)
    private BloomLevel bloomLevel;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
