package swd392.group6.AIVES.questionbank.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Citation of a material passage by an AI-generated question (BR-Q5). */
@Entity
@Table(name = "question_sources")
@Getter
@Setter
@NoArgsConstructor
public class QuestionSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "question_source_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "location_label", nullable = false, length = 100)
    private String locationLabel;

    @Column(name = "excerpt")
    private String excerpt;
}
