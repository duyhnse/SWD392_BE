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

/** One row of a đề thi: "N câu chương X mức Bloom Y, mỗi câu S giây" (D45, D46). {@code exam_template_items}. */
@Entity
@Table(name = "exam_template_items")
@Getter
@NoArgsConstructor
@AllArgsConstructor
class ExamTemplateItem {

    @Id
    @Column(name = "template_item_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "exam_template_id", nullable = false, updatable = false)
    private UUID templateId;

    /** null = any topic of the course. */
    @Column(name = "topic_id")
    private UUID topicId;

    /** null = any Bloom level. */
    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", length = 30)
    private BloomLevel bloomLevel;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    /** Budget of one main question including its follow-ups. */
    @Column(name = "seconds_per_question", nullable = false)
    private int secondsPerQuestion;


    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
