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

/** STT hotword of a course. */
@Entity
@Table(name = "course_terms")
@Getter
@Setter
@NoArgsConstructor
public class CourseTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "course_term_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "term", nullable = false, length = 100)
    private String term;

    public CourseTerm(UUID courseId, String term) {
        this.courseId = courseId;
        this.term = term;
    }
}
