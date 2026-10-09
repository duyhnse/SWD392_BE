package swd392.group6.AIVES.exam;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.user.User;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.BloomLevel.APPLY;
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** Buổi thi CRUD, config validation, blueprint, pool, student list and authorization (15 §5.3, §6, 07 §3). */
@IntegrationTest
@Import(ExamTestConfiguration.class)
class ExamCrudIntegrationTest extends ExamTestBase {

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    @Test
    void createListGetPatchDelete() throws Exception {
        Course c = course();
        String body = json(call(post("/api/v1/courses/" + c.id() + "/viva-exams"), c.token(),
                "{\"title\":\" Giữa kỳ \",\"windowStart\":\"" + T0.plusSeconds(86400) + "\",\"windowEnd\":\""
                        + T0.plusSeconds(90000) + "\",\"mainQuestionCount\":3,\"timeLimitPerStudentSec\":600,"
                        + "\"location\":\"P.301\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Giữa kỳ"))
                .andExpect(jsonPath("$.examinerId").value(c.lecturer().getUserId().toString()))
                .andExpect(jsonPath("$.language").value("VI"))
                .andExpect(jsonPath("$.questionPool.mode").value("COURSE_BANK"))
                .andExpect(jsonPath("$.stageCounts.UPCOMING").value(0)));
        String id = JsonPath.read(body, "$.id");
        int version = JsonPath.read(body, "$.version");
        UUID past = createExam(c, T0.minusSeconds(7200), T0.minusSeconds(3600), 2, null);

        call(get("/api/v1/courses/" + c.id() + "/viva-exams?when=upcoming"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id));
        call(get("/api/v1/courses/" + c.id() + "/viva-exams?when=past&status=DRAFT"), c.token())
                .andExpect(jsonPath("$.items[*].id", contains(past.toString())));
        call(get("/api/v1/courses/" + c.id() + "/viva-exams?status=READY"), c.token())
                .andExpect(jsonPath("$.total").value(0));

        call(patch("/api/v1/viva-exams/" + id), c.token(),
                "{\"version\":" + version + ",\"title\":\"Cuối kỳ\",\"mainQuestionCount\":4,\"location\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cuối kỳ"))
                .andExpect(jsonPath("$.mainQuestionCount").value(4))
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.version").value(version + 1));
        call(patch("/api/v1/viva-exams/" + id), c.token(), "{\"version\":" + version + ",\"title\":\"Stale\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        call(delete("/api/v1/viva-exams/" + id), c.token()).andExpect(status().isNoContent());
        call(get("/api/v1/viva-exams/" + id), c.token())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIVA_EXAM_NOT_FOUND"));
    }

    @Test
    void configIsValidated() throws Exception {
        Course c = course();
        String url = "/api/v1/courses/" + c.id() + "/viva-exams";
        String window = "\"windowStart\":\"" + T0 + "\",\"windowEnd\":\"" + T0.plusSeconds(3600) + "\"";
        String[][] cases = {
                {"\"mainQuestionCount\":3,\"timeLimitPerStudentSec\":100", "INVALID_TIME_LIMIT"},
                {"\"mainQuestionCount\":10,\"timeLimitPerStudentSec\":300", "INVALID_TIME_LIMIT"},
                {"\"mainQuestionCount\":11,\"timeLimitPerStudentSec\":900", "INVALID_QUESTION_COUNT"},
                {"\"maxFollowupsPerQuestion\":6", "INVALID_FOLLOWUP_COUNT"},
                {"\"answerTimeLimitSec\":10", "INVALID_ANSWER_TIME_LIMIT"},
                {"\"examinerId\":\"" + data.lecturer().getUserId() + "\"", "INVALID_EXAMINER"},
                {"\"topicIds\":[\"" + UUID.randomUUID() + "\"]", "TOPIC_NOT_IN_COURSE"},
        };
        for (String[] tc : cases) {
            call(post(url), c.token(), "{\"title\":\"X\"," + window + "," + tc[0] + "}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value(tc[1]));
        }
        call(post(url), c.token(), "{\"title\":\"X\",\"windowStart\":\"" + T0 + "\",\"windowEnd\":\"" + T0 + "\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_WINDOW"));
        call(post(url), c.token(), "{\"windowStart\":\"" + T0 + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAssignedLecturersManage_adminReads_AC_C1() throws Exception {
        Course c = course();
        UUID exam = createExam(c, 2);
        String other = token(data.lecturer());
        String admin = token(data.admin());
        String student = token(data.student());

        call(get("/api/v1/viva-exams/" + exam), other).andExpect(status().isNotFound());
        call(get("/api/v1/courses/" + c.id() + "/viva-exams"), other).andExpect(status().isNotFound());
        call(patch("/api/v1/viva-exams/" + exam), other, "{\"version\":0,\"title\":\"x\"}").andExpect(status().isNotFound());
        call(post("/api/v1/viva-exams/" + exam + "/generate-sessions"), other).andExpect(status().isNotFound());
        call(get("/api/v1/viva-exams/" + exam), student).andExpect(status().isNotFound());
        call(get("/api/v1/viva-exams/" + exam + "/sessions"), student).andExpect(status().isNotFound());

        call(get("/api/v1/viva-exams/" + exam), admin).andExpect(status().isOk());
        call(get("/api/v1/courses/" + c.id() + "/viva-exams"), admin).andExpect(jsonPath("$.total").value(1));
        call(get("/api/v1/viva-exams/" + exam + "/students"), admin).andExpect(status().isOk());
        call(post("/api/v1/courses/" + c.id() + "/viva-exams"), admin,
                "{\"title\":\"X\",\"windowStart\":\"" + T0 + "\",\"windowEnd\":\"" + T0.plusSeconds(3600) + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_READ_ONLY"));
        call(delete("/api/v1/viva-exams/" + exam), admin).andExpect(status().isForbidden());
        call(post("/api/v1/viva-exams/" + exam + "/open"), admin).andExpect(status().isForbidden());

        call(get("/api/v1/me/sessions"), c.token()).andExpect(status().isForbidden());
    }

    @Test
    void blueprintMustMatchTheCourseAndTheQuestionCount() throws Exception {
        Course c = course();
        Course otherCourse = course();
        UUID exam = createExam(c, 3);
        String url = "/api/v1/viva-exams/" + exam + "/blueprint";

        call(put(url), c.token(), "{\"items\":[{\"topicId\":\"" + otherCourse.topicA() + "\",\"count\":3}]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_IN_COURSE"));
        call(put(url), c.token(), "{\"items\":[{\"topicId\":\"" + c.topicA() + "\",\"bloomLevel\":\"UNDERSTAND\",\"count\":2}]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BLUEPRINT_COUNT_MISMATCH"));
        call(put(url), c.token(), "{\"items\":[{\"topicId\":\"" + c.topicA() + "\",\"bloomLevel\":\"UNDERSTAND\",\"count\":2},"
                + "{\"topicId\":\"" + c.topicB() + "\",\"bloomLevel\":\"APPLY\",\"count\":1}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blueprint", hasSize(2)))
                .andExpect(jsonPath("$.blueprint[0].bloomLevel").value("UNDERSTAND"))
                .andExpect(jsonPath("$.blueprint[1].count").value(1));
        call(get("/api/v1/viva-exams/" + exam), c.token()).andExpect(jsonPath("$.blueprint", hasSize(2)));
        call(put(url), c.token(), "{\"items\":[]}").andExpect(jsonPath("$.blueprint", hasSize(0)));
    }

    @Test
    void selectedPoolOnlyTakesPublishedQuestionsOfTheCourse() throws Exception {
        Course c = course();
        Course otherCourse = course();
        UUID exam = createExam(c, 2);
        List<UUID> published = questions(c, c.topicA(), UNDERSTAND, 3);
        UUID draft = data.question(c.id(), c.topicA(), APPLY, c.rubric(), c.lecturer(), "DRAFT");
        UUID foreign = data.question(otherCourse.id(), otherCourse.topicA(), APPLY, otherCourse.rubric(), otherCourse.lecturer());
        String url = "/api/v1/viva-exams/" + exam + "/question-pool";

        for (UUID bad : List.of(draft, foreign)) {
            call(put(url), c.token(), "{\"mode\":\"SELECTED\",\"questionIds\":[\"" + published.get(0) + "\",\"" + bad + "\"]}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("QUESTION_NOT_PUBLISHED"));
        }
        call(put(url), c.token(), "{\"mode\":\"SELECTED\",\"questionIds\":[]}")
                .andExpect(status().isUnprocessableContent());
        call(put(url), c.token(), "{\"mode\":\"SELECTED\",\"questionIds\":[\"" + published.get(0) + "\",\""
                + published.get(1) + "\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionPool.mode").value("SELECTED"))
                .andExpect(jsonPath("$.questionPool.questionIds", hasSize(2)))
                .andExpect(jsonPath("$.questionPool.availableCount").value(2));
        call(put(url), c.token(), "{\"mode\":\"COURSE_BANK\"}")
                .andExpect(jsonPath("$.questionPool.mode").value("COURSE_BANK"))
                .andExpect(jsonPath("$.questionPool.availableCount").value(3));
    }

    @Test
    void pastedCodesAreMatchedCaseInsensitivelyAndUnknownOnesReported_AC_C3() throws Exception {
        Course c = course();
        UUID exam = createExam(c, 2);
        User s1 = data.student();
        User s2 = data.student();
        User lecturer = data.lecturer();
        String url = "/api/v1/viva-exams/" + exam + "/students";

        call(put(url), c.token(), "{\"studentCodes\":\"" + s1.getStudentCode() + ", " + s2.getStudentCode().toLowerCase()
                + " SE999999\\n" + s1.getStudentCode() + "\",\"usernames\":[\"" + lecturer.getUsername() + "\",\"nobody-x\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", hasSize(2)))
                .andExpect(jsonPath("$.added[0].studentId").value(s1.getUserId().toString()))
                .andExpect(jsonPath("$.added[1].seqNo").value(2))
                .andExpect(jsonPath("$.unknown", contains("SE999999", "nobody-x")))
                .andExpect(jsonPath("$.notStudent", contains(lecturer.getUsername())));
        call(put(url), c.token(), "{\"usernames\":\"" + s2.getUsername().toUpperCase() + "\"}")
                .andExpect(jsonPath("$.added", hasSize(0)))
                .andExpect(jsonPath("$.alreadyInExam", contains(s2.getUsername())));
        assertThat(jdbc.queryForObject("select count(*) from users where student_code = 'SE999999'", Integer.class)).isZero();

        User s3 = data.student();
        addStudents(c, exam, s3);
        call(delete(url + "/" + s1.getUserId()), c.token()).andExpect(status().isNoContent());
        call(get(url), c.token())
                .andExpect(jsonPath("$[*].studentId", contains(s2.getUserId().toString(), s3.getUserId().toString())))
                .andExpect(jsonPath("$[*].seqNo", contains(1, 2)))
                .andExpect(jsonPath("$[0].stage").doesNotExist());
        call(delete(url + "/" + s1.getUserId()), c.token()).andExpect(status().isNotFound());
    }

    @Test
    void classListCsvCreatesMissingStudentsThenAddsThem() throws Exception {
        Course c = course();
        UUID exam = createExam(c, 2);
        User existing = data.student();
        String fresh = "n" + UUID.randomUUID().toString().substring(0, 8);
        String csv = "username,full_name,email,student_code\n"
                + existing.getUsername() + "," + existing.getFullName() + "," + existing.getEmail() + ",\n"
                + fresh + ",Nguyễn Mới," + fresh + "@fpt.edu.vn,\n"
                + "bad,No Email,not-an-email,\n";

        call(multipart("/api/v1/viva-exams/" + exam + "/students/import")
                .file(new MockMultipartFile("file", "class.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.added[*].username", contains(existing.getUsername(), fresh)))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].row").value(4));
    }

    @Test
    void configIsFrozenOnceQuestionSetsExist() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 4);
        UUID exam = createExam(c, 2);
        User s = data.student();
        addStudents(c, exam, s);
        generate(c, exam);
        int version = JsonPath.read(json(call(get("/api/v1/viva-exams/" + exam), c.token())), "$.version");

        call(patch("/api/v1/viva-exams/" + exam), c.token(), "{\"version\":" + version + ",\"mainQuestionCount\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_CONFIG_FROZEN"));
        call(patch("/api/v1/viva-exams/" + exam), c.token(), "{\"version\":" + version + ",\"instructions\":\"Bring ID\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructions").value("Bring ID"));
        call(delete("/api/v1/viva-exams/" + exam), c.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_DRAFT"));
        call(put("/api/v1/viva-exams/" + exam + "/students"), c.token(), "{\"studentCodes\":[\"X\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_CONFIG_FROZEN"));
        call(put("/api/v1/viva-exams/" + exam + "/blueprint"), c.token(), "{\"items\":[]}")
                .andExpect(status().isConflict());
        call(post("/api/v1/viva-exams/" + exam + "/generate-sessions"), c.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_DRAFT"));
    }
}
