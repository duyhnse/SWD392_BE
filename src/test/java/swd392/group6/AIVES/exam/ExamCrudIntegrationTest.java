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
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** Buổi thi CRUD, rules validation, student list and authorization (15 §5.3, §6, D47). */
@IntegrationTest
@Import(ExamTestConfiguration.class)
class ExamCrudIntegrationTest extends ExamTestBase {

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    @Test
    void createListGetPatchDelete_D47() throws Exception {
        Course c = course();
        UUID template = template(c, 3);
        String body = json(call(post("/api/v1/courses/" + c.id() + "/viva-exams"), c.token(),
                "{\"title\":\" Giữa kỳ \",\"templateId\":\"" + template + "\",\"checkinOpensAt\":\""
                        + T0.plusSeconds(86400) + "\",\"checkinClosesAt\":\"" + T0.plusSeconds(90000)
                        + "\",\"location\":\"P.301\",\"maxDisconnects\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Giữa kỳ"))
                .andExpect(jsonPath("$.examinerId").value(c.lecturer().getUserId().toString()))
                .andExpect(jsonPath("$.template.id").value(template.toString()))
                .andExpect(jsonPath("$.template.mainQuestionCount").value(3))
                // no fixed end time: duration = Σ question budgets (3 × 120 s), last end = closing + duration + frozen
                .andExpect(jsonPath("$.durationSec").value(360))
                .andExpect(jsonPath("$.lastPossibleEndAt").value(T0.plusSeconds(90000 + 360 + 180).toString()))
                .andExpect(jsonPath("$.maxDisconnects").value(2))
                .andExpect(jsonPath("$.reconnectGraceSec").value(60))
                .andExpect(jsonPath("$.stageCounts.UPCOMING").value(0)));
        String id = JsonPath.read(body, "$.id");
        int version = JsonPath.read(body, "$.version");
        createExam(c, template(c, 2), T0.minusSeconds(7200), T0.minusSeconds(3600), null);

        call(get("/api/v1/courses/" + c.id() + "/viva-exams?when=upcoming"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].durationSec").value(360));
        call(get("/api/v1/courses/" + c.id() + "/viva-exams?when=past"), c.token()).andExpect(jsonPath("$.total").value(1));

        call(patch("/api/v1/viva-exams/" + id), c.token(), "{\"version\":" + version + ",\"instructions\":\"Bật micro\","
                + "\"checkinClosesAt\":\"" + T0.plusSeconds(100000) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructions").value("Bật micro"))
                .andExpect(jsonPath("$.checkinClosesAt").value(T0.plusSeconds(100000).toString()));
        call(patch("/api/v1/viva-exams/" + id), c.token(), "{\"version\":" + version + ",\"title\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        call(delete("/api/v1/viva-exams/" + id), c.token()).andExpect(status().isNoContent());
        call(get("/api/v1/viva-exams/" + id), c.token()).andExpect(status().isNotFound());
    }

    @Test
    void rulesAreValidated() throws Exception {
        Course c = course();
        UUID template = template(c, 2);
        String url = "/api/v1/courses/" + c.id() + "/viva-exams";
        String window = ",\"checkinOpensAt\":\"" + T0 + "\",\"checkinClosesAt\":\"" + T0.plusSeconds(3600) + "\"";
        call(post(url), c.token(), "{\"title\":\"X\",\"templateId\":\"" + template + "\",\"checkinOpensAt\":\""
                + T0 + "\",\"checkinClosesAt\":\"" + T0 + "\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_WINDOW"));
        call(post(url), c.token(), "{\"title\":\"X\",\"templateId\":\"" + template + "\"" + window
                + ",\"reconnectGraceSec\":5}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_CONFIG"));
        call(post(url), c.token(), "{\"title\":\"X\",\"templateId\":\"" + template + "\"" + window
                + ",\"examinerId\":\"" + data.lecturer().getUserId() + "\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_EXAMINER"));
        Course other = course();
        call(post(url), c.token(), "{\"title\":\"X\",\"templateId\":\"" + template(other, 1) + "\"" + window + "}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("TEMPLATE_NOT_IN_COURSE"));
        call(post(url), c.token(), "{\"title\":\"X\"" + window + "}")
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
        call(post("/api/v1/viva-exams/" + exam + "/publish"), other).andExpect(status().isNotFound());
        call(get("/api/v1/viva-exams/" + exam), student).andExpect(status().isNotFound());
        call(get("/api/v1/viva-exams/" + exam + "/attempts"), student).andExpect(status().isNotFound());

        call(get("/api/v1/viva-exams/" + exam), admin).andExpect(status().isOk());
        call(get("/api/v1/courses/" + c.id() + "/viva-exams"), admin).andExpect(jsonPath("$.total").value(1));
        call(get("/api/v1/viva-exams/" + exam + "/students"), admin).andExpect(status().isOk());
        call(get("/api/v1/viva-exams/" + exam + "/attempts"), admin).andExpect(status().isOk());
        call(post("/api/v1/courses/" + c.id() + "/viva-exams"), admin,
                "{\"title\":\"X\",\"templateId\":\"" + UUID.randomUUID() + "\",\"checkinOpensAt\":\"" + T0
                        + "\",\"checkinClosesAt\":\"" + T0.plusSeconds(3600) + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_READ_ONLY"));
        call(delete("/api/v1/viva-exams/" + exam), admin).andExpect(status().isForbidden());
        call(post("/api/v1/viva-exams/" + exam + "/open"), admin).andExpect(status().isForbidden());

        call(get("/api/v1/me/viva-exams"), c.token()).andExpect(status().isForbidden());
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
                .andExpect(jsonPath("$[0].stage").value("UPCOMING"))
                .andExpect(jsonPath("$[0].attemptId").doesNotExist());
        call(delete(url + "/" + s1.getUserId()), c.token()).andExpect(status().isNotFound());
    }

    @Test
    void classListCsvCreatesMissingStudentsThenAddsThem() throws Exception {
        Course c = course();
        UUID exam = createExam(c, 2);
        User existing = data.student();
        String fresh = "n" + UUID.randomUUID().toString().substring(0, 8);
        String csv = "username,full_name,email,student_code\n"
                + existing.getUsername() + "," + existing.getFullName() + "," + existing.getEmail() + "," + existing.getStudentCode() + "\n"
                + fresh + ",Nguyễn Mới," + fresh + "@fpt.edu.vn,SE" + fresh.substring(1).toUpperCase() + "\n"
                + "bad,No Email,not-an-email,SEBAD1\n";

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
    void publishedExamFreezesEverythingButTextsAndTheClosingTime() throws Exception {
        Course c = course();
        questions(c, c.chapterA(), UNDERSTAND, 4);
        UUID exam = createExam(c, 2);
        addStudents(c, exam, data.student());
        int version = JsonPath.read(publish(c, exam), "$.exam.version");

        call(patch("/api/v1/viva-exams/" + exam), c.token(), "{\"version\":" + version + ",\"maxDisconnects\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_CONFIG_FROZEN"));
        call(patch("/api/v1/viva-exams/" + exam), c.token(), "{\"version\":" + version + ",\"location\":\"P.302\","
                + "\"checkinClosesAt\":\"" + T0.plusSeconds(9000) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("P.302"));
        call(delete("/api/v1/viva-exams/" + exam), c.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_DRAFT"));
        // late roster changes are fine: questions are only drawn at check-in (D48)
        addStudents(c, exam, data.student());
    }
}
