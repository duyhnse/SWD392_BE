package swd392.group6.AIVES.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.user.CrudFixtures.unique;

/** 15 §5.1 GET / PUT /admin/settings (FG7 language & speech configuration). */
@IntegrationTest
class SettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private CrudFixtures fx;
    private User admin;
    private String adminAuth;

    @BeforeEach
    void setUp() throws Exception {
        fx = new CrudFixtures(jdbc, userRepository, passwordEncoder);
        admin = fx.user(Role.ADMIN, "adm" + unique());
        adminAuth = CrudFixtures.bearer(mockMvc, admin);
    }

    private void putSettings(String body) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/settings")
                        .header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void adminListsSeededSettings() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.key=='stt.provider')].description").isNotEmpty())
                .andExpect(jsonPath("$[?(@.key=='stt.language.vi')].value").value("vi"))
                .andExpect(jsonPath("$[0].updatedAt").isNotEmpty());
    }

    @Test
    void adminUpdatesSettingsAndDefaultLanguageAppliesToNewCourses() throws Exception {
        try {
            putSettings("{\"settings\":{\"tts.voice.en\":\"en-US-AriaNeural\",\"default_language\":\"EN\"}}");
            mockMvc.perform(get("/api/v1/admin/settings").header("Authorization", adminAuth))
                    .andExpect(jsonPath("$[?(@.key=='tts.voice.en')].value").value("en-US-AriaNeural"))
                    .andExpect(jsonPath("$[?(@.key=='default_language')].value").value("EN"));
            assertThat(jdbc.queryForObject("select updated_by from system_settings where setting_key = 'tts.voice.en'",
                    java.util.UUID.class)).isEqualTo(admin.getUserId());

            String code = "SET" + unique().toUpperCase().substring(0, 6);
            mockMvc.perform(post("/api/v1/admin/courses").header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"" + code + "\",\"name\":\"x\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.defaultLanguage").value("EN"));
        } finally {
            putSettings("{\"settings\":{\"tts.voice.en\":\"\",\"default_language\":\"VI\"}}");
        }
    }

    @Test
    void unknownKeyIsRejectedAndNothingIsSaved() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":{\"stt.provider\":\"openai\",\"llm.secret\":\"x\"}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNKNOWN_SETTING"));
        mockMvc.perform(get("/api/v1/admin/settings").header("Authorization", adminAuth))
                .andExpect(jsonPath("$[?(@.key=='stt.provider')].value").value("mock"));
    }

    @Test
    void invalidValuesAreRejected() throws Exception {
        for (String body : new String[]{
                "{\"settings\":{\"default_language\":\"FR\"}}",
                "{\"settings\":{\"stt.provider\":42}}",
                "{\"settings\":{\"stt.provider\":null}}"}) {
            mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", adminAuth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTING_VALUE"));
        }
        mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SETTINGS_EMPTY"));
    }

    @Test
    void onlyAdminsManageSettings() throws Exception {
        for (Role role : new Role[]{Role.LECTURER, Role.STUDENT}) {
            String auth = CrudFixtures.bearer(mockMvc, fx.user(role, "x" + unique()));
            mockMvc.perform(get("/api/v1/admin/settings").header("Authorization", auth)).andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"settings\":{\"stt.provider\":\"x\"}}")).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/admin/settings")).andExpect(status().isUnauthorized());
    }
}
