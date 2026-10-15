package swd392.group6.AIVES.grading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.grading.ExamFixtures.CourseFx;
import swd392.group6.AIVES.grading.ExamFixtures.RubricFx;
import swd392.group6.AIVES.grading.ExamFixtures.SessionFx;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG6 class report and score sheet export (15 §5.5). */
@IntegrationTest
class ReportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private JdbcTemplate jdbc;

    private ExamFixtures fx;
    private CourseFx course;
    private UUID examId;
    private String lecturerToken;
    private UUID q1;
    private UUID q2;
    private UUID q3;
    private User an;
    private User binh;
    private User chi;
    private User dung;

    /**
     * Four students: An (6.80 + 10.00 → 8.40, confirmed), Bình (5.00 + 4.00 → 4.50, confirmed), Chi (completed,
     * not yet graded), Dung (no-show). An and Bình got q1+q2, Chi got q1+q3.
     */
    @BeforeEach
    void setUp() throws Exception {
        fx = new ExamFixtures(jdbc, users);
        course = fx.course();
        RubricFx rubric = fx.rubric(course, 10, 60, 10, 40);
        examId = fx.exam(course, 2);
        lecturerToken = "Bearer " + TestUsers.login(mockMvc, course.lecturer().getUsername(), TestUsers.PASSWORD);
        GradingClient grading = new GradingClient(mockMvc, lecturerToken);
        q1 = fx.question(course, rubric.rubricId(), "Câu dễ");
        q2 = fx.question(course, rubric.rubricId(), "Câu khó");
        q3 = fx.question(course, rubric.rubricId(), "Câu thường");

        an = fx.student("Nguyễn An");
        SessionFx sAn = fx.completedSession(examId, course, an, List.of(q1, q2));
        fx.answered(sAn, 0, q1, "a");
        fx.answered(sAn, 1, q2, "b");
        grading.gradeAndConfirm(sAn.sessionId(), new double[] {8, 5}, new double[] {10, 10});

        binh = fx.student("Lê Bình, \"B\"");
        SessionFx sBinh = fx.completedSession(examId, course, binh, List.of(q1, q2));
        fx.answered(sBinh, 0, q1, "a");
        fx.answered(sBinh, 1, q2, "b");
        grading.gradeAndConfirm(sBinh.sessionId(), new double[] {5, 5}, new double[] {4, 4});

        chi = fx.student("Phạm Chi");
        SessionFx sChi = fx.completedSession(examId, course, chi, List.of(q1, q3));
        fx.answered(sChi, 0, q1, "a");
        fx.answered(sChi, 1, q3, "b");
        grading.create(sChi.sessionId());

        dung = fx.student("Đỗ Dung");
        fx.session(examId, course, dung, "NO_SHOW", null, List.of(q1, q3), List.of("PENDING", "PENDING"));
        // Check-in has closed: Dung, who never checked in, is MISSED (D48)
        jdbc.update("update viva_exams set status = 'CLOSED', checkin_closes_at = now() - interval '1 minute' where viva_exam_id = ?",
                examId);
    }

    @Test
    void reportComputesCountsStatisticsAndHardestQuestions() throws Exception {
        mockMvc.perform(get("/api/v1/viva-exams/{id}/report", examId).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rosterSize").value(4))
                .andExpect(jsonPath("$.totalAttempts").value(3))
                .andExpect(jsonPath("$.attemptsByStatus.COMPLETED").value(3))
                .andExpect(jsonPath("$.attemptsByStatus.CANCELLED").value(0))
                .andExpect(jsonPath("$.studentsByStage.COMPLETED").value(3))
                .andExpect(jsonPath("$.studentsByStage.MISSED").value(1))
                .andExpect(jsonPath("$.studentsByStage.UPCOMING").value(0))
                .andExpect(jsonPath("$.passScore").doesNotExist())
                .andExpect(jsonPath("$.evaluatedCount").value(3))
                .andExpect(jsonPath("$.confirmedCount").value(2))
                .andExpect(jsonPath("$.finalTotals.count").value(2))
                .andExpect(jsonPath("$.finalTotals.mean").value(6.45))
                .andExpect(jsonPath("$.finalTotals.median").value(6.45))
                .andExpect(jsonPath("$.finalTotals.min").value(4.5))
                .andExpect(jsonPath("$.finalTotals.max").value(8.4))
                .andExpect(jsonPath("$.distribution", hasSize(10)))
                .andExpect(jsonPath("$.distribution[4].count").value(1))
                .andExpect(jsonPath("$.distribution[8].count").value(1))
                .andExpect(jsonPath("$.distribution[0].count").value(0))
                // q1: asked 3×, graded 2× (6.80, 5.00) → avg 5.90, 0 good answers
                .andExpect(jsonPath("$.questions[0].questionId").value(q1.toString()))
                .andExpect(jsonPath("$.questions[0].timesAsked").value(3))
                .andExpect(jsonPath("$.questions[0].gradedCount").value(2))
                .andExpect(jsonPath("$.questions[0].averageFinalScore").value(5.9))
                .andExpect(jsonPath("$.questions[0].goodAnswerRate").value(0.0))
                // q2: 10.00 and 4.00 → 7.00, half good
                .andExpect(jsonPath("$.questions[?(@.questionId == '" + q2 + "')].averageFinalScore").value(7.0))
                .andExpect(jsonPath("$.questions[?(@.questionId == '" + q2 + "')].goodAnswerRate").value(0.5))
                .andExpect(jsonPath("$.questions[?(@.questionId == '" + q3 + "')].timesAsked").value(1))
                .andExpect(jsonPath("$.questions[?(@.questionId == '" + q3 + "')].gradedCount").value(0))
                .andExpect(jsonPath("$.hardestQuestions", hasSize(2)))
                .andExpect(jsonPath("$.hardestQuestions[0].questionId").value(q1.toString()))
                .andExpect(jsonPath("$.hardestQuestions[1].questionId").value(q2.toString()));

        mockMvc.perform(get("/api/v1/viva-exams/{id}/report", examId)
                        .header("Authorization", "Bearer " + TestUsers.login(mockMvc,
                                users.create(Role.LECTURER).getUsername(), TestUsers.PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/viva-exams/{id}/report", UUID.randomUUID()).header("Authorization", lecturerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIVA_EXAM_NOT_FOUND"));
    }

    @Test
    void passMarkOfTheTemplateCountsPassedStudentsAndAddsAResultColumn() throws Exception {
        jdbc.update("""
                update exam_templates set pass_score = 5 where exam_template_id =
                  (select exam_template_id from viva_exams where viva_exam_id = ?)""", examId);
        mockMvc.perform(get("/api/v1/viva-exams/{id}/report", examId).header("Authorization", lecturerToken))
                .andExpect(jsonPath("$.passScore").value(5.0))
                .andExpect(jsonPath("$.passedCount").value(1));
        String csv = mockMvc.perform(get("/api/v1/viva-exams/{id}/export", examId).header("Authorization", lecturerToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> lines = csv.substring(1).lines().toList();
        assertThat(lines.get(0)).isEqualTo("STT,MSSV,Họ tên,Câu 1,Câu 2,Tổng,Kết quả,Ghi chú");
        assertThat(lines.get(1)).endsWith(",8.40,Đạt,");
        assertThat(lines.get(2)).endsWith(",4.50,Không đạt,");
        assertThat(lines.get(4)).endsWith(",,,,,Vắng thi");
    }

    @Test
    void exportIsAUtf8CsvScoreSheetWithBom() throws Exception {
        byte[] body = mockMvc.perform(get("/api/v1/viva-exams/{id}/export", examId).param("format", "csv")
                        .header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(Arrays.copyOf(body, 3)).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        List<String> lines = csv.lines().toList();
        assertThat(lines).hasSize(5);
        assertThat(lines.get(0)).isEqualTo("STT,MSSV,Họ tên,Câu 1,Câu 2,Tổng,Ghi chú");
        assertThat(lines.get(1)).isEqualTo("1," + an.getStudentCode() + ",Nguyễn An,6.80,10.00,8.40,");
        assertThat(lines.get(2)).isEqualTo("2," + binh.getStudentCode() + ",\"Lê Bình, \"\"B\"\"\",5.00,4.00,4.50,");
        assertThat(lines.get(3)).isEqualTo("3," + chi.getStudentCode() + ",Phạm Chi,,,,Chưa xác nhận điểm");
        assertThat(lines.get(4)).isEqualTo("4," + dung.getStudentCode() + ",Đỗ Dung,,,,Vắng thi");

        mockMvc.perform(get("/api/v1/viva-exams/{id}/export", examId).param("format", "pdf")
                        .header("Authorization", lecturerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FORMAT"));
        User admin = users.create(Role.ADMIN);
        mockMvc.perform(get("/api/v1/viva-exams/{id}/export", examId)
                        .header("Authorization", "Bearer " + TestUsers.login(mockMvc, admin.getUsername(), TestUsers.PASSWORD)))
                .andExpect(status().isOk());
    }
}
