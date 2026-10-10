package swd392.group6.AIVES.exam;

import org.junit.jupiter.api.Test;
import swd392.group6.AIVES.exam.QuestionSelector.Candidate;
import swd392.group6.AIVES.exam.QuestionSelector.Draw;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static swd392.group6.AIVES.questionbank.BloomLevel.ANALYZE;
import static swd392.group6.AIVES.questionbank.BloomLevel.APPLY;
import static swd392.group6.AIVES.questionbank.BloomLevel.REMEMBER;
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** Random draw at check-in with template rows — 15 §3, D48 (AC-C4, AC-E3, AC-E4, AC-E7). Overlap is allowed. */
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

    private static List<Row> any(int n) {
        return List.of(new Row(null, null, n));
    }

    private static List<UUID> ids(Draw d) {
        return d.picks().stream().map(QuestionSelector.Pick::questionId).toList();
    }

    /** Students checking in one after another, as {@code CheckInService} does (usage counted per buổi thi). */
    private static List<List<UUID>> checkIns(List<Candidate> pool, List<Row> rows, int students) {
        Map<UUID, Integer> usage = new HashMap<>();
        List<List<UUID>> result = new ArrayList<>();
        for (int i = 0; i < students; i++) {
            List<UUID> mine = ids(QuestionSelector.draw(pool, rows, Set.of(), usage, SEED + i));
            mine.forEach(id -> usage.merge(id, 1, Integer::sum));
            result.add(mine);
        }
        return result;
    }

    @Test
    void everyStudentGetsTheTemplateMix_AC_C4() {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            pool.add(q(TOPIC_A, UNDERSTAND));
        }
        pool.add(q(TOPIC_B, APPLY));
        pool.add(q(TOPIC_A, APPLY));        // distractors: right topic or right level only
        pool.add(q(TOPIC_B, UNDERSTAND));
        Map<UUID, Candidate> byId = new HashMap<>();
        pool.forEach(c -> byId.put(c.questionId(), c));
        List<Row> rows = List.of(new Row(TOPIC_A, UNDERSTAND, 2), new Row(TOPIC_B, APPLY, 1));

        for (List<UUID> mine : checkIns(pool, rows, 5)) {
            assertThat(mine.stream().map(byId::get).toList()).extracting(Candidate::topicId, Candidate::bloomLevel)
                    .containsExactly(tuple(TOPIC_A, UNDERSTAND), tuple(TOPIC_A, UNDERSTAND), tuple(TOPIC_B, APPLY));
        }
    }

    @Test
    void studentsMayShareQuestionsWhenThePoolIsSmall() {
        List<List<UUID>> drawn = checkIns(pool(3), any(3), 2);
        assertThat(drawn.get(0)).hasSize(3);
        assertThat(drawn.get(1)).containsExactlyInAnyOrderElementsOf(drawn.get(0));
    }

    @Test
    void leastUsedQuestionsComeFirstSoUseIsSpread() {
        Set<UUID> used = new HashSet<>();
        checkIns(pool(9), any(3), 3).forEach(used::addAll);
        assertThat(used).hasSize(9);
    }

    @Test
    void poolSmallerThanARowIsAShortage_AC_E3() {
        List<QuestionSelector.RowShortage> shortages = QuestionSelector.shortages(pool(2), any(3));
        assertThat(shortages).singleElement().satisfies(s -> {
            assertThat(s.required()).isEqualTo(3);
            assertThat(s.available()).isEqualTo(2);
        });
        List<Candidate> small = List.of(q(TOPIC_A, UNDERSTAND), q(TOPIC_B, APPLY), q(TOPIC_B, APPLY));
        assertThat(QuestionSelector.shortages(small, List.of(new Row(TOPIC_A, UNDERSTAND, 2), new Row(TOPIC_B, APPLY, 1))))
                .singleElement().satisfies(s -> {
                    assertThat(s.rowIndex()).isZero();
                    assertThat(s.topicId()).isEqualTo(TOPIC_A);
                });
        assertThat(QuestionSelector.shortages(pool(3), any(3))).isEmpty();
    }

    @Test
    void questionsAreOrderedByBloomAscending_AC_E4() {
        List<Candidate> pool = pool(12);
        Map<UUID, BloomLevel> bloom = new HashMap<>();
        pool.forEach(c -> bloom.put(c.questionId(), c.bloomLevel()));
        for (List<UUID> mine : checkIns(pool, any(4), 5)) {
            List<Integer> levels = mine.stream().map(id -> bloom.get(id).ordinal()).toList();
            List<Integer> sorted = new ArrayList<>(levels);
            Collections.sort(sorted);
            assertThat(levels).isEqualTo(sorted);
        }
    }

    @Test
    void sameSeedGivesTheSameDraw_AC_E7() {
        List<Candidate> pool = pool(20);
        List<Candidate> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        assertThat(ids(QuestionSelector.draw(shuffled, any(3), Set.of(), Map.of(), SEED)))
                .isEqualTo(ids(QuestionSelector.draw(pool, any(3), Set.of(), Map.of(), SEED)));
    }

    @Test
    void rowsWithoutBloomSpreadOverLevels() {
        List<Candidate> pool = List.of(q(TOPIC_A, REMEMBER), q(TOPIC_A, REMEMBER), q(TOPIC_A, REMEMBER),
                q(TOPIC_A, ANALYZE), q(TOPIC_A, ANALYZE));
        Map<UUID, BloomLevel> bloom = new HashMap<>();
        pool.forEach(c -> bloom.put(c.questionId(), c.bloomLevel()));
        assertThat(ids(QuestionSelector.draw(pool, any(2), Set.of(), Map.of(), SEED)).stream().map(bloom::get))
                .containsExactly(REMEMBER, ANALYZE);
    }

    @Test
    void excludedQuestionsAreNeverDrawn() {
        List<Candidate> pool = pool(4);
        Set<UUID> excluded = Set.of(pool.get(0).questionId(), pool.get(1).questionId());
        assertThat(ids(QuestionSelector.draw(pool, any(2), excluded, Map.of(), SEED))).doesNotContainAnyElementsOf(excluded);
        assertThat(QuestionSelector.draw(pool, any(3), excluded, Map.of(), SEED).failed()).isTrue();
    }
}
