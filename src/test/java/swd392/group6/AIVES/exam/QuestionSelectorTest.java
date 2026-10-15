package swd392.group6.AIVES.exam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import swd392.group6.AIVES.exam.QuestionSelector.Candidate;
import swd392.group6.AIVES.exam.QuestionSelector.Examinee;
import swd392.group6.AIVES.exam.QuestionSelector.Result;
import swd392.group6.AIVES.exam.QuestionSelector.Row;
import swd392.group6.AIVES.questionbank.BloomLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static swd392.group6.AIVES.questionbank.BloomLevel.ANALYZE;
import static swd392.group6.AIVES.questionbank.BloomLevel.APPLY;
import static swd392.group6.AIVES.questionbank.BloomLevel.REMEMBER;
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** RANDOM_BALANCED with blueprint — 07 §2, 15 §3 (AC-E1..E4, AC-E7, AC-C4, AC-C5). */
class QuestionSelectorTest {

    private static final UUID TOPIC_A = new UUID(0, 0xA);
    private static final UUID TOPIC_B = new UUID(0, 0xB);
    private static final long SEED = 42L;

    private static int counter;

    private static Candidate q(UUID topic, BloomLevel bloom) {
        return new Candidate(new UUID(1, ++counter), topic, bloom);
    }

    private static List<Candidate> pool(int n) {
        BloomLevel[] levels = BloomLevel.values();
        return IntStream.range(0, n).mapToObj(i -> q(TOPIC_A, levels[i % levels.length])).toList();
    }

    private static List<Examinee> students(int n) {
        return IntStream.range(0, n).mapToObj(i -> new Examinee(new UUID(2, i), Set.of())).toList();
    }

    private static List<Row> any(int n) {
        return List.of(new Row(null, null, n));
    }

    private static Set<String> warningCodes(Result r) {
        Set<String> codes = new HashSet<>();
        r.warnings().forEach(w -> codes.add(w.code()));
        return codes;
    }

    static Stream<Arguments> consecutiveCases() {
        // poolSize, N, students, overlap expected
        return Stream.of(
                Arguments.of(6, 3, 4, false),   // AC-E1
                Arguments.of(10, 5, 6, false),
                Arguments.of(7, 3, 5, false),
                Arguments.of(3, 3, 2, true),    // AC-E2
                Arguments.of(5, 3, 3, true));
    }

    @ParameterizedTest(name = "pool {0}, N {1}, {2} students → overlap warning {3}")
    @MethodSource("consecutiveCases")
    void consecutiveStudentsShareNoQuestionWhenThePoolAllows(int poolSize, int n, int studentCount, boolean overlap) {
        List<Examinee> students = students(studentCount);
        Result r = QuestionSelector.select(pool(poolSize), any(n), students, SEED);

        assertThat(r.failed()).isFalse();
        for (int i = 0; i < students.size(); i++) {
            List<UUID> mine = r.assignments().get(students.get(i).studentId());
            assertThat(mine).hasSize(n).doesNotHaveDuplicates();
            if (i > 0 && !overlap) {
                assertThat(mine).doesNotContainAnyElementsOf(r.assignments().get(students.get(i - 1).studentId()));
            }
        }
        assertThat(warningCodes(r).contains(QuestionSelector.OVERLAP_UNAVOIDABLE)).isEqualTo(overlap);
    }

    @Test
    void poolSmallerThanNFails_AC_E3() {
        Result r = QuestionSelector.select(pool(2), any(3), students(2), SEED);

        assertThat(r.failed()).isTrue();
        assertThat(r.shortages()).singleElement().satisfies(s -> {
            assertThat(s.required()).isEqualTo(3);
            assertThat(s.available()).isEqualTo(2);
        });
    }

    @Test
    void questionsAreOrderedByBloomAscending_AC_E4() {
        List<Candidate> pool = pool(12);
        Map<UUID, BloomLevel> bloom = new HashMap<>();
        pool.forEach(c -> bloom.put(c.questionId(), c.bloomLevel()));

        Result r = QuestionSelector.select(pool, any(4), students(5), SEED);

        r.assignments().values().forEach(ids -> {
            List<Integer> levels = ids.stream().map(id -> bloom.get(id).ordinal()).toList();
            List<Integer> sorted = new ArrayList<>(levels);
            Collections.sort(sorted);
            assertThat(levels).isEqualTo(sorted);
        });
    }

    @Test
    void sameSeedGivesSameAssignment_AC_E7() {
        List<Candidate> pool = pool(20);
        List<Candidate> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);

        Result first = QuestionSelector.select(pool, any(3), students(8), SEED);
        Result second = QuestionSelector.select(shuffled, any(3), students(8), SEED);

        assertThat(second.assignments()).isEqualTo(first.assignments());
    }

    @Test
    void leastUsedQuestionsComeFirst() {
        Result r = QuestionSelector.select(pool(9), any(3), students(3), SEED);

        Set<UUID> used = new HashSet<>();
        r.assignments().values().forEach(used::addAll);
        assertThat(used).hasSize(9);
    }

    @Test
    void rowsWithoutBloomSpreadOverLevels() {
        List<Candidate> pool = List.of(q(TOPIC_A, REMEMBER), q(TOPIC_A, REMEMBER), q(TOPIC_A, REMEMBER), q(TOPIC_A, REMEMBER),
                q(TOPIC_A, ANALYZE), q(TOPIC_A, ANALYZE));
        Map<UUID, BloomLevel> bloom = new HashMap<>();
        pool.forEach(c -> bloom.put(c.questionId(), c.bloomLevel()));

        Result r = QuestionSelector.select(pool, any(2), students(2), SEED);

        r.assignments().values().forEach(ids ->
                assertThat(ids.stream().map(bloom::get)).containsExactly(REMEMBER, ANALYZE));
    }

    @Test
    void blueprintGivesEveryStudentTheExactMix_AC_C4() {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            pool.add(q(TOPIC_A, UNDERSTAND));
        }
        pool.add(q(TOPIC_B, APPLY));
        pool.add(q(TOPIC_B, APPLY));
        pool.add(q(TOPIC_A, APPLY));        // distractors: right topic or right level only
        pool.add(q(TOPIC_B, UNDERSTAND));
        Map<UUID, Candidate> byId = new HashMap<>();
        pool.forEach(c -> byId.put(c.questionId(), c));
        List<Row> blueprint = List.of(new Row(TOPIC_A, UNDERSTAND, 2), new Row(TOPIC_B, APPLY, 1));
        List<Examinee> students = students(5);

        Result r = QuestionSelector.select(pool, blueprint, students, SEED);

        assertThat(r.failed()).isFalse();
        assertThat(warningCodes(r)).doesNotContain(QuestionSelector.OVERLAP_UNAVOIDABLE);
        for (int i = 0; i < students.size(); i++) {
            List<Candidate> mine = r.assignments().get(students.get(i).studentId()).stream().map(byId::get).toList();
            assertThat(mine).extracting(Candidate::topicId, Candidate::bloomLevel).containsExactly(
                    org.assertj.core.groups.Tuple.tuple(TOPIC_A, UNDERSTAND),
                    org.assertj.core.groups.Tuple.tuple(TOPIC_A, UNDERSTAND),
                    org.assertj.core.groups.Tuple.tuple(TOPIC_B, APPLY));
            if (i > 0) {
                assertThat(r.assignments().get(students.get(i).studentId()))
                        .doesNotContainAnyElementsOf(r.assignments().get(students.get(i - 1).studentId()));
            }
        }
    }

    @Test
    void blueprintRowThatCannotBeSatisfiedIsReported() {
        List<Candidate> pool = List.of(q(TOPIC_A, UNDERSTAND), q(TOPIC_B, APPLY), q(TOPIC_B, APPLY));
        List<Row> blueprint = List.of(new Row(TOPIC_A, UNDERSTAND, 2), new Row(TOPIC_B, APPLY, 1));

        Result r = QuestionSelector.select(pool, blueprint, students(2), SEED);

        assertThat(r.failed()).isTrue();
        assertThat(r.shortages()).singleElement().satisfies(s -> {
            assertThat(s.rowIndex()).isZero();
            assertThat(s.topicId()).isEqualTo(TOPIC_A);
            assertThat(s.available()).isEqualTo(1);
        });
    }

    @Test
    void smallPoolIsAWarning() {
        Result r = QuestionSelector.select(pool(5), any(3), students(1), SEED);

        assertThat(warningCodes(r)).contains(QuestionSelector.POOL_SMALL);
    }

    @Test
    void retakeExcludesPreviousQuestions_AC_C5() {
        List<Candidate> pool = pool(8);
        Set<UUID> before = Set.of(pool.get(0).questionId(), pool.get(1).questionId(), pool.get(2).questionId());
        Examinee retaker = new Examinee(new UUID(3, 1), before);

        Result r = QuestionSelector.select(pool, any(3), List.of(new Examinee(new UUID(3, 0), Set.of()), retaker), SEED);

        assertThat(r.assignments().get(retaker.studentId())).doesNotContainAnyElementsOf(before);
        assertThat(warningCodes(r)).doesNotContain(QuestionSelector.RETAKE_OVERLAP_UNAVOIDABLE);
    }

    @Test
    void retakeOverlapIsAWarningWhenThePoolIsTooSmall() {
        List<Candidate> pool = pool(4);
        Set<UUID> before = Set.of(pool.get(0).questionId(), pool.get(1).questionId(), pool.get(2).questionId());

        Result r = QuestionSelector.select(pool, any(3), List.of(new Examinee(new UUID(3, 1), before)), SEED);

        assertThat(r.failed()).isFalse();
        assertThat(r.assignments().get(new UUID(3, 1))).contains(pool.get(3).questionId());
        assertThat(warningCodes(r)).contains(QuestionSelector.RETAKE_OVERLAP_UNAVOIDABLE);
    }
}
