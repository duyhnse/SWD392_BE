package swd392.group6.AIVES.questionbank.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reusable, course-scoped grading rubric (BR-Q3, BR-Q10). */
@Entity
@Table(name = "rubrics")
@Getter
@Setter
@NoArgsConstructor
public class Rubric {

    /** BR-Q3: weights must total 100 within this tolerance. */
    public static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.01");
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rubric_id", nullable = false, updatable = false)
    private UUID id;

    /** Short number shown as a code (D56); assigned by the database sequence on insert. */
    @org.hibernate.annotations.Generated
    @Column(name = "display_no", insertable = false, updatable = false)
    private Long displayNo;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "rubric", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, name ASC")
    private List<RubricCriterion> criteria = new ArrayList<>();

    public BigDecimal totalWeight() {
        return criteria.stream().map(RubricCriterion::getWeightPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** BR-Q3: at least one criterion, every max score positive, weights summing to 100 (±0.01). */
    public boolean isValidForPublish() {
        return !criteria.isEmpty()
                && criteria.stream().allMatch(c -> c.getMaxScore().signum() > 0 && c.getWeightPercent().signum() > 0)
                && totalWeight().subtract(HUNDRED).abs().compareTo(WEIGHT_TOLERANCE) <= 0;
    }

    public void addCriterion(RubricCriterion criterion) {
        criterion.setRubric(this);
        criteria.add(criterion);
    }
}
