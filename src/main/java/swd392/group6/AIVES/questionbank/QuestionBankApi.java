package swd392.group6.AIVES.questionbank;

import swd392.group6.AIVES.common.Language;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public API of the question bank for exam, interview and grading (04 §12, 15 §5.2). */
public interface QuestionBankApi {

    /**
     * PUBLISHED questions of a course. Empty/null filters mean "no restriction".
     *
     * @param restrictToIds when not empty, only these question ids (SELECTED pool mode)
     */
    List<PublishedQuestion> findPublished(UUID courseId, Collection<UUID> topicIds, Collection<BloomLevel> bloomLevels,
                                          Collection<UUID> restrictToIds);

    Optional<QuestionInfo> getQuestion(UUID questionId);

    /** Rubric of the question with its criteria. Empty when the question has no rubric. */
    Optional<RubricSnapshot> getRubricSnapshot(UUID questionId);

    /** A rubric with its criteria (exam-template overrides). Empty when it does not exist. */
    Optional<RubricSnapshot> getRubric(UUID rubricId);

    boolean topicBelongsToCourse(UUID topicId, UUID courseId);

    /** True when the rubric exists, belongs to the course and its weights total 100 (BR-Q3). */
    boolean rubricUsableInCourse(UUID rubricId, UUID courseId);

    /** Topics of a course by id (for exam-template views). */
    Map<UUID, TopicInfo> topics(UUID courseId);

    /**
     * Everything an attempt must keep about the drawn questions (D49): content, reference answer, topic, Bloom,
     * language, version and the question's own rubric.
     */
    Map<UUID, QuestionSnapshot> snapshot(Collection<UUID> questionIds);

    /**
     * Locks questions drawn into an attempt and their rubrics (BR-Q8 since D49): further changes need a successor.
     * Bumps the question version so concurrent editors get {@code VERSION_CONFLICT}.
     */
    void lockForExam(Collection<UUID> questionIds, Collection<UUID> rubricIds);

    record PublishedQuestion(UUID questionId, UUID courseId, UUID topicId, BloomLevel bloomLevel, Language language,
                             String content) {
    }

    record QuestionInfo(UUID questionId, UUID courseId, UUID topicId, String content, String referenceAnswer,
                        BloomLevel bloomLevel, Language language, String status, UUID rubricId, boolean locked) {
    }

    record TopicInfo(UUID topicId, String name, int sortOrder) {
    }

    record QuestionSnapshot(UUID questionId, UUID topicId, String topicName, String content,
                            String referenceAnswer, BloomLevel bloomLevel, Language language, int version,
                            RubricSnapshot rubric) {
    }

    record RubricSnapshot(UUID rubricId, String name, List<Criterion> criteria) {

        public record Criterion(UUID criterionId, String name, String description, BigDecimal maxScore,
                                BigDecimal weightPercent, int sortOrder) {
        }
    }
}
