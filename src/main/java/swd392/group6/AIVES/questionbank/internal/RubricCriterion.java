package swd392.group6.AIVES.questionbank.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rubric_criteria")
@Getter
@Setter
@NoArgsConstructor
public class RubricCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "criterion_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_id", nullable = false, updatable = false)
    private Rubric rubric;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public RubricCriterion(String name, String description, BigDecimal maxScore, BigDecimal weightPercent, int sortOrder) {
        this.name = name;
        this.description = description;
        this.maxScore = maxScore;
        this.weightPercent = weightPercent;
        this.sortOrder = sortOrder;
    }
}
