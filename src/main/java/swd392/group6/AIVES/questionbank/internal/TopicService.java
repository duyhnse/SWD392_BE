package swd392.group6.AIVES.questionbank.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.CreateTopicRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.TermsDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.TopicDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.UpdateTopicRequest;
import swd392.group6.AIVES.user.User;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Topics CRUD and course terms (STT hotwords) — 15 §5.2. */
@Service
@RequiredArgsConstructor
@Transactional
public class TopicService {

    static final int MAX_TERMS = 200;
    static final int MAX_TERM_LENGTH = 100;
    private static final UUID NO_ID = new UUID(0, 0);

    private final TopicRepository topics;
    private final QuestionRepository questions;
    private final CourseTermRepository terms;
    private final ContentAccess access;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TopicDto> list(UUID courseId, User user) {
        access.readCourse(courseId, user);
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : questions.countByTopic(courseId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return topics.findByCourseIdOrderBySortOrderAscNameAsc(courseId).stream()
                .map(t -> toDto(t, counts.getOrDefault(t.getId(), 0L))).toList();
    }

    public TopicDto create(UUID courseId, CreateTopicRequest request, User user) {
        access.writeCourse(courseId, user);
        String name = request.name().trim();
        if (topics.nameTaken(courseId, name, NO_ID)) {
            throw nameExists(name);
        }
        Topic topic = new Topic();
        topic.setCourseId(courseId);
        topic.setName(name);
        topic.setDescription(blankToNull(request.description()));
        topic.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        topic.setCreatedBy(user.getUserId());
        topic.setCreatedAt(Instant.now(clock));
        return toDto(topics.save(topic), 0);
    }

    public TopicDto update(UUID topicId, UpdateTopicRequest request, User user) {
        Topic topic = load(topicId);
        access.write(topic.getCourseId(), user, "TOPIC_NOT_FOUND", "Topic not found");
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw ApiException.unprocessable("TOPIC_NAME_REQUIRED", "Topic name must not be blank");
            }
            if (topics.nameTaken(topic.getCourseId(), name, topic.getId())) {
                throw nameExists(name);
            }
            topic.setName(name);
        }
        if (request.description() != null) {
            topic.setDescription(blankToNull(request.description()));
        }
        if (request.sortOrder() != null) {
            topic.setSortOrder(request.sortOrder());
        }
        return toDto(topic, questionCount(topic.getId()));
    }

    public void delete(UUID topicId, User user) {
        Topic topic = load(topicId);
        access.write(topic.getCourseId(), user, "TOPIC_NOT_FOUND", "Topic not found");
        Boolean usedElsewhere = jdbc.queryForObject("""
                select exists(select 1 from viva_exam_blueprint_items where topic_id = ?)
                    or exists(select 1 from ai_generation_requests where topic_id = ?)""",
                Boolean.class, topicId, topicId);
        if (questions.existsByTopicId(topicId) || Boolean.TRUE.equals(usedElsewhere)) {
            throw ApiException.conflict("TOPIC_IN_USE", "The topic still has questions or is used by an exam blueprint");
        }
        topics.delete(topic);
    }

    @Transactional(readOnly = true)
    public TermsDto getTerms(UUID courseId, User user) {
        access.readCourse(courseId, user);
        return new TermsDto(terms.findByCourse(courseId).stream().map(CourseTerm::getTerm).toList());
    }

    /** Replaces the list: trimmed, blanks dropped, de-duplicated case-insensitively (first spelling wins). */
    public TermsDto replaceTerms(UUID courseId, TermsDto request, User user) {
        access.writeCourse(courseId, user);
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : request.terms()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String term = raw.trim();
            if (term.length() > MAX_TERM_LENGTH) {
                throw ApiException.unprocessable("TERM_TOO_LONG",
                        "Each term must be at most " + MAX_TERM_LENGTH + " characters: " + term.substring(0, 20) + "…");
            }
            if (seen.add(term.toLowerCase(Locale.ROOT))) {
                cleaned.add(term);
            }
        }
        if (cleaned.size() > MAX_TERMS) {
            throw ApiException.unprocessable("TOO_MANY_TERMS", "A course can have at most " + MAX_TERMS + " terms");
        }
        terms.deleteByCourse(courseId);
        terms.saveAll(cleaned.stream().map(t -> new CourseTerm(courseId, t)).toList());
        terms.flush();
        return new TermsDto(terms.findByCourse(courseId).stream().map(CourseTerm::getTerm).toList());
    }

    private Topic load(UUID topicId) {
        return topics.findById(topicId).orElseThrow(() -> ApiException.notFound("TOPIC_NOT_FOUND", "Topic not found"));
    }

    private long questionCount(UUID topicId) {
        Long n = jdbc.queryForObject("select count(*) from questions where topic_id = ?", Long.class, topicId);
        return n == null ? 0 : n;
    }

    private static ApiException nameExists(String name) {
        return ApiException.conflict("TOPIC_NAME_EXISTS", "A topic named \"" + name + "\" already exists in this course");
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static TopicDto toDto(Topic t, long questionCount) {
        return new TopicDto(t.getId(), t.getCourseId(), t.getName(), t.getDescription(), t.getSortOrder(),
                questionCount, t.getCreatedBy(), t.getCreatedAt());
    }
}
