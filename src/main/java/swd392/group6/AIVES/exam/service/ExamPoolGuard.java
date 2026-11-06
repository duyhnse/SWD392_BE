package swd392.group6.AIVES.exam.service;

import swd392.group6.AIVES.exam.domain.ExamTemplate;
import swd392.group6.AIVES.exam.repository.ExamTemplateItemRepository;
import swd392.group6.AIVES.exam.repository.ExamTemplateRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.questionbank.PublishedPoolGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps published / open buổi thi drawable (15 §5.6): a change to the published bank is refused when one of them
 * would no longer find a full set of questions at check-in. The lecturer unpublishes or cancels the buổi thi first,
 * or publishes replacement questions.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExamPoolGuard implements PublishedPoolGuard {

    private final NamedParameterJdbcTemplate jdbc;
    private final ExamTemplateRepository templates;
    private final ExamTemplateItemRepository items;
    private final ExamQueries queries;

    @Override
    public void check(UUID courseId, List<PoolChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        Map<UUID, PoolChange> byQuestion = changes.stream().collect(Collectors.toMap(PoolChange::questionId, Function.identity(), (a, b) -> b));
        record Live(long no, String title, UUID templateId) {
        }
        List<Live> live = jdbc.query("""
                        select e.display_no, e.title, e.exam_template_id from viva_exams e
                        where e.course_id = :c and e.status in ('READY', 'OPEN') order by e.display_no""",
                new MapSqlParameterSource("c", courseId), (rs, i) -> new Live(rs.getLong(1), rs.getString(2), rs.getObject(3, UUID.class)));
        List<String> blocked = new ArrayList<>();
        for (Live exam : live) {
                    UUID templateId = exam.templateId();
                    ExamTemplate t = templates.findById(templateId).orElse(null);
                    if (t == null) {
                        continue;
                    }
                    List<QuestionSelector.Candidate> pool = new ArrayList<>();
                    for (QuestionSelector.Candidate c : queries.candidates(t)) {
                        PoolChange change = byQuestion.get(c.questionId());
                        if (change == null) {
                            pool.add(c);
                        } else if (!change.removed()) {
                            pool.add(new QuestionSelector.Candidate(c.questionId(), change.topicId(), change.bloomLevel()));
                        }
                    }
                    if (!QuestionSelector.shortages(pool, ExamQueries.rows(items.findByTemplateIdOrderBySortOrder(templateId))).isEmpty()) {
                        blocked.add("B-" + exam.no() + " \"" + exam.title() + "\"");
                    }
        }
        if (!blocked.isEmpty()) {
            throw ApiException.conflict("QUESTION_NEEDED_BY_EXAM", "Published exam sessions would lack questions: "
                    + String.join(", ", blocked) + ". Publish replacement questions first, or unpublish / cancel them.");
        }
    }
}
