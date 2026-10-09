package swd392.group6.AIVES.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.support.IntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.user.CrudFixtures.unique;

/** 15 §5.1 courses + lecturer assignment, §6 authorization, AC-C1 / AC-C11. */
@IntegrationTest
class CourseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CourseAccessApi courseAccess;

    private CrudFixtures fx;
    private String adminAuth;

    @BeforeEach
    void setUp() throws Exception {
        fx = new CrudFixtures(jdbc, userRepository, passwordEncoder);
        adminAuth = CrudFixtures.bearer(mockMvc, fx.user(Role.ADMIN, "adm" + unique()));
    }

    private static String code() {
        return "C" + unique().toUpperCase().substring(0, 8);
    }

    private UUID createCourse(String code) throws Exception {
        String json = mockMvc.perform(post("/api/v1/admin/courses").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Software Architecture\",\"description\":\"d\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.courseId"));
    }

    // --- create / update ----------------------------------------------------------------------------

    @Test
    void adminCreatesCourseWithUppercaseCodeAndDefaultLanguage() throws Exception {
        String code = code();
        mockMvc.perform(post("/api/v1/admin/courses").header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" " + code.toLowerCase() + " \",\"name\":\"Kiến trúc phần mềm\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("Kiến trúc phần mềm"))
                .andExpect(jsonPath("$.defaultLanguage").value("VI"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.lecturers.length()").value(0));
    }

    @Test
    void duplicateCodeIsConflictCaseInsensitive() throws Exception {
        String code = code();
        createCourse(code);
        mockMvc.perform(post("/api/v1/admin/courses").header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.toLowerCase() + "\",\"name\":\"Other\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_CODE_EXISTS"));
        UUID other = createCourse(code());
        mockMvc.perform(patch("/api/v1/admin/courses/" + other).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_CODE_EXISTS"));
    }

    @Test
    void createValidatesInput() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses").header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/admin/courses").header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SWD 392!\",\"name\":\"x\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_COURSE_CODE"));
    }

    @Test
    void adminUpdatesAndDeactivatesCourse() throws Exception {
        UUID id = createCourse(code());
        mockMvc.perform(patch("/api/v1/admin/courses/" + id).header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New name\",\"defaultLanguage\":\"EN\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.defaultLanguage").value("EN"))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(patch("/api/v1/admin/courses/" + UUID.randomUUID()).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    // --- lecturers ------------------------------------------------------------------------------------

    @Test
    void assignmentReplacesLecturersAndDrivesCourseAccess() throws Exception {
        UUID id = createCourse(code());
        User l1 = fx.user(Role.LECTURER, "lec" + unique());
        User l2 = fx.user(Role.LECTURER, "lec" + unique());

        mockMvc.perform(put("/api/v1/admin/courses/" + id + "/lecturers").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerIds\":[\"" + l1.getUserId() + "\",\"" + l2.getUserId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturers.length()").value(2));
        assertThat(courseAccess.isLecturerOf(id, l1.getUserId())).isTrue();

        mockMvc.perform(put("/api/v1/admin/courses/" + id + "/lecturers").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lecturerIds\":[\"" + l2.getUserId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturers.length()").value(1))
                .andExpect(jsonPath("$.lecturers[0].userId").value(l2.getUserId().toString()));
        assertThat(courseAccess.isLecturerOf(id, l1.getUserId())).isFalse();
        assertThat(courseAccess.assignedCourseIds(l2.getUserId())).contains(id);
    }

    @Test
    void onlyActiveLecturersCanBeAssigned() throws Exception {
        UUID id = createCourse(code());
        User student = fx.user(Role.STUDENT, "stu" + unique());
        User inactive = fx.user(Role.LECTURER, "lec" + unique());
        inactive.setActive(false);
        userRepository.save(inactive);

        for (UUID bad : new UUID[]{student.getUserId(), inactive.getUserId(), UUID.randomUUID()}) {
            mockMvc.perform(put("/api/v1/admin/courses/" + id + "/lecturers").header("Authorization", adminAuth)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"lecturerIds\":[\"" + bad + "\"]}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("NOT_A_LECTURER"));
        }
        mockMvc.perform(put("/api/v1/admin/courses/" + id + "/lecturers").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- read access ----------------------------------------------------------------------------------

    @Test
    void lecturerSeesOnlyAssignedCoursesAndGets404OnOthers_AC_C1() throws Exception {
        String prefix = "Q" + unique().toUpperCase().substring(0, 6);
        UUID mine = createCourse(prefix + "A");
        UUID notMine = createCourse(prefix + "B");
        User lecturer = fx.user(Role.LECTURER, "lec" + unique());
        fx.assign(mine, lecturer.getUserId());
        String lecturerAuth = CrudFixtures.bearer(mockMvc, lecturer);

        mockMvc.perform(get("/api/v1/courses").param("q", prefix).header("Authorization", lecturerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].courseId").value(mine.toString()))
                .andExpect(jsonPath("$.items[0].lecturerCount").value(1));
        mockMvc.perform(get("/api/v1/courses/" + mine).header("Authorization", lecturerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturers[0].userId").value(lecturer.getUserId().toString()));
        mockMvc.perform(get("/api/v1/courses/" + notMine).header("Authorization", lecturerAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    void lecturerWithoutAssignmentsSeesNothing() throws Exception {
        String auth = CrudFixtures.bearer(mockMvc, fx.user(Role.LECTURER, "lec" + unique()));
        mockMvc.perform(get("/api/v1/courses").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void adminListsAllCoursesFilteredByActive() throws Exception {
        String prefix = "P" + unique().toUpperCase().substring(0, 6);
        createCourse(prefix + "A");
        UUID b = createCourse(prefix + "B");
        mockMvc.perform(patch("/api/v1/admin/courses/" + b).header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses").param("q", prefix.toLowerCase()).header("Authorization", adminAuth))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/v1/courses").param("q", prefix).param("active", "true").header("Authorization", adminAuth))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].code").value(prefix + "A"));
        mockMvc.perform(get("/api/v1/courses/" + b).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void studentsAndOutsidersCannotUseCourseEndpoints() throws Exception {
        UUID id = createCourse(code());
        String student = CrudFixtures.bearer(mockMvc, fx.user(Role.STUDENT, "stu" + unique()));
        String lecturer = CrudFixtures.bearer(mockMvc, fx.user(Role.LECTURER, "lec" + unique()));

        mockMvc.perform(get("/api/v1/courses").header("Authorization", student)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/courses/" + id).header("Authorization", student)).andExpect(status().isForbidden());
        for (String auth : new String[]{student, lecturer}) {
            mockMvc.perform(post("/api/v1/admin/courses").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + code() + "\",\"name\":\"x\"}")).andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/v1/admin/courses/" + id).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}")).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/admin/courses/" + id).header("Authorization", auth)).andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/admin/courses/" + id + "/lecturers").header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"lecturerIds\":[]}")).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/courses")).andExpect(status().isUnauthorized());
    }

    // --- delete ---------------------------------------------------------------------------------------

    @Test
    void emptyCourseIsDeletedTogetherWithAssignments() throws Exception {
        UUID id = createCourse(code());
        fx.assign(id, fx.user(Role.LECTURER, "lec" + unique()).getUserId());
        mockMvc.perform(delete("/api/v1/admin/courses/" + id).header("Authorization", adminAuth))
                .andExpect(status().isNoContent());
        assertThat(courseAccess.courseExists(id)).isFalse();
        mockMvc.perform(delete("/api/v1/admin/courses/" + id).header("Authorization", adminAuth))
                .andExpect(status().isNotFound());
    }

    @Test
    void courseWithContentIsInUse_AC_C11() throws Exception {
        UUID id = createCourse(code());
        User lecturer = fx.user(Role.LECTURER, "lec" + unique());
        fx.assign(id, lecturer.getUserId());
        fx.chapter(id, lecturer.getUserId());

        mockMvc.perform(delete("/api/v1/admin/courses/" + id).header("Authorization", adminAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_IN_USE"));
        assertThat(courseAccess.courseExists(id)).isTrue();
        assertThat(courseAccess.isLecturerOf(id, lecturer.getUserId())).isTrue();
    }
}
