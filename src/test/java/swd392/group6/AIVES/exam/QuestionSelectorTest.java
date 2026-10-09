package swd392.group6.AIVES.exam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import swd392.group6.AIVES.exam.QuestionSelector.Candidate;
import swd392.group6.AIVES.exam.QuestionSelector.Row;
import swd392.group6.AIVES.questionbank.BloomLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

/**
 * RANDOM_BALANCED draw at check-in with template rows — 07 §2, 15 §3, D48 (AC-E1..E4, AC-E7, AC-C4, AC-C5).
 * {@link #simulate} checks students in one after another, as {@code CheckInService} does.
 */
class QuestionSelectorTest {

    record Examinee(UUID studentId, Set<UUID> excluded) {
    }

    record Result(Map<UUID, List<UUID>> assignments, List<QuestionSelector.Warning> warnings,
                  List<QuestionSelector.RowShortage> shortages) {

        boolean failed() {
            return !shortages.isEmpty();
        }
    }

    /** Coverage check (publish) + one draw per student: previous examinee's questions avoided, usage counted. */
    static Result simulate(List<Candidate> pool, List<Row> rows, List<Examinee> examinees, long seed) {
        QuestionSelector.Coverage coverage = QuestionSelector.coverage(pool, rows);
        if (!coverage.shortages().isEmpty()) {
            return new Result(Map.of(), List.of(), coverage.shortages());
        }
        List<QuestionSelector.Warning> warnings = new ArrayList<>(coverage.warnings());
        Map<UUID, List<UUID>> assignments = new LinkedHashMap<>();
        Map<UUID, Integer> usage = new HashMap<>();
        Set<UUID> previous = Set.of();
        int i = 0;
        for (Examinee e : examinees) {
            QuestionSelector.Draw draw = QuestionSelector.draw(pool, rows, e.studentId(), e.excluded(), previous, usage,
                    seed + i++);
            if (draw.failed()) {
                return new Result(Map.of(), List.of(), draw.shortages());
            }
            List<UUID> ids = draw.picks().stream().map(QuestionSelector.Pick::questionId).toList();
            ids.forEach(id -> usage.merge(id, 1, Integer::sum));
            warnings.addAll(draw.warnings());
            assignments.put(e.studentId(), ids);
            previous = new LinkedHashSet<>(ids);
        }
        return new Result(assignments, warnings, List.of());
    }

    private static final UUID CHAPTER_A = new UUID(0, 0xA);
    private static final UUID CHAPTER_B = new UUID(0, 0xB);
    private static final long SEED = 42L;

    private static int counter;

    private static Candidate q(UUID chapter, BloomLevel bloom) {
        return new Candidate(new UUID(1, ++counter), chapter, bloom);
    }

    private static List<Candidate> pool(int n) {
        BloomLevel[] levels = BloomLevel.values();
        return IntStream.range(0, n).mapToObj(i -> q(CHAPTER_A, levels[i % levels.length])).toList();
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
        Result r = simulate(pool(poolSize), any(n), students, SEED);

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
        Result r = simulate(pool(2), any(3), students(2), SEED);

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

        Result r = simulate(pool, any(4), students(5), SEED);

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

        Result first = simulate(pool, any(3), students(8), SEED);
        Result second = simulate(shuffled, any(3), students(8), SEED);

        assertThat(second.assignments()).isEqualTo(first.assignments());
    }

    @Test
    void leastUsedQuestionsComeFirst() {
        Result r = simulate(pool(9), any(3), students(3), SEED);

        Set<UUID> used = new HashSet<>();
        r.assignments().values().forEach(used::addAll);
        assertThat(used).hasSize(9);
    }

    @Test
    void rowsWithoutBloomSpreadOverLevels() {
        List<Candidate> pool = List.of(q(CHAPTER_A, REMEMBER), q(CHAPTER_A, REMEMBER), q(CHAPTER_A, REMEMBER), q(CHAPTER_A, REMEMBER),
                q(CHAPTER_A, ANALYZE), q(CHAPTER_A, ANALYZE));
        Map<UUID, BloomLevel> bloom = new HashMap<>();
        pool.forEach(c -> bloom.put(c.questionId(), c.bloomLevel()));

        Result r = simulate(pool, any(2), students(2), SEED);

        r.assignments().values().forEach(ids ->
                assertThat(ids.stream().map(bloom::get)).containsExactly(REMEMBER, ANALYZE));
    }

    @Test
    void blueprintGivesEveryStudentTheExactMix_AC_C4() {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            pool.add(q(CHAPTER_A, UNDERSTAND));
        }
        pool.add(q(CHAPTER_B, APPLY));
        pool.add(q(CHAPTER_B, APPLY));
        pool.add(q(CHAPTER_A, APPLY));        // distractors: right chapter or right level only
        pool.add(q(CHAPTER_B, UNDERSTAND));
        Map<UUID, Candidate> byId = new HashMap<>();
        pool.forEach(c -> byId.put(c.questionId(), c));
        List<Row> blueprint = List.of(new Row(CHAPTER_A, UNDERSTAND, 2), new Row(CHAPTER_B, APPLY, 1));
        List<Examinee> students = students(5);

        Result r = simulate(pool, blueprint, students, SEED);

        assertThat(r.failed()).isFalse();
        assertThat(warningCodes(r)).doesNotContain(QuestionSelector.OVERLAP_UNAVOIDABLE);
        for (int i = 0; i < students.size(); i++) {
            List<Candidate> mine = r.assignments().get(students.get(i).studentId()).stream().map(byId::get).toList();
            assertThat(mine).extracting(Candidate::chapterId, Candidate::bloomLevel).containsExactly(
                    org.assertj.core.groups.Tuple.tuple(CHAPTER_A, UNDERSTAND),
                    org.assertj.core.groups.Tuple.tuple(CHAPTER_A, UNDERSTAND),
                    org.assertj.core.groups.Tuple.tuple(CHAPTER_B, APPLY));
            if (i > 0) {
                assertThat(r.assignments().get(students.get(i).studentId()))
                        .doesNotContainAnyElementsOf(r.assignments().get(students.get(i - 1).studentId()));
            }
        }
    }

    @Test
    void blueprintRowThatCannotBeSatisfiedIsReported() {
        List<Candidate> pool = List.of(q(CHAPTER_A, UNDERSTAND), q(CHAPTER_B, APPLY), q(CHAPTER_B, APPLY));
        List<Row> blueprint = List.of(new Row(CHAPTER_A, UNDERSTAND, 2), new Row(CHAPTER_B, APPLY, 1));

        Result r = simulate(pool, blueprint, students(2), SEED);

        assertThat(r.failed()).isTrue();
        assertThat(r.shortages()).singleElement().satisfies(s -> {
            assertThat(s.rowIndex()).isZero();
            assertThat(s.chapterId()).isEqualTo(CHAPTER_A);
            assertThat(s.available()).isEqualTo(1);
        });
    }

    @Test
    void smallPoolIsAWarning() {
        Result r = simulate(pool(5), any(3), students(1), SEED);

        assertThat(warningCodes(r)).contains(QuestionSelector.POOL_SMALL);
    }

    @Test
    void retakeExcludesPreviousQuestions_AC_C5() {
        List<Candidate> pool = pool(8);
        Set<UUID> before = Set.of(pool.get(0).questionId(), pool.get(1).questionId(), pool.get(2).questionId());
        Examinee retaker = new Examinee(new UUID(3, 1), before);

        Result r = simulate(pool, any(3), List.of(new Examinee(new UUID(3, 0), Set.of()), retaker), SEED);

        assertThat(r.assignments().get(retaker.studentId())).doesNotContainAnyElementsOf(before);
        assertThat(warningCodes(r)).doesNotContain(QuestionSelector.RETAKE_OVERLAP_UNAVOIDABLE);
    }

    @Test
    void retakeOverlapIsAWarningWhenThePoolIsTooSmall() {
        List<Candidate> pool = pool(4);
        Set<UUID> before = Set.of(pool.get(0).questionId(), pool.get(1).questionId(), pool.get(2).questionId());

        Result r = simulate(pool, any(3), List.of(new Examinee(new UUID(3, 1), before)), SEED);

        assertThat(r.failed()).isFalse();
        assertThat(r.assignments().get(new UUID(3, 1))).contains(pool.get(3).questionId());
        assertThat(warningCodes(r)).contains(QuestionSelector.RETAKE_OVERLAP_UNAVOIDABLE);
    }
}
