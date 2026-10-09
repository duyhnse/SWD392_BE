package swd392.group6.AIVES.questionbank;

import swd392.group6.AIVES.common.Language;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
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

    /** Rubric of the question with its criteria, for grading snapshots. Empty when the question has no rubric. */
    Optional<RubricSnapshot> getRubricSnapshot(UUID questionId);

    boolean topicBelongsToCourse(UUID topicId, UUID courseId);

    record PublishedQuestion(UUID questionId, UUID courseId, UUID topicId, BloomLevel bloomLevel, Language language,
                             String content) {
    }

    record QuestionInfo(UUID questionId, UUID courseId, UUID topicId, String content, String referenceAnswer,
                        BloomLevel bloomLevel, Language language, String status, UUID rubricId, boolean locked) {
    }

    record RubricSnapshot(UUID rubricId, String name, List<Criterion> criteria) {

        public record Criterion(UUID criterionId, String name, String description, BigDecimal maxScore,
                                BigDecimal weightPercent, int sortOrder) {
        }
    }
}
