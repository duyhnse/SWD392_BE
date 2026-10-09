package swd392.group6.AIVES.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;
import swd392.group6.AIVES.user.UserRepository;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Creates accounts straight in the database (there is no sign-up endpoint) and logs them in. */
@Component
public class TestUsers {

    public static final String PASSWORD = "S3curePassw0rd";
    /** All test logins come from one "browser", so re-logging in is never asked to sign out another device (D38). */
    public static final String DEVICE = "test-device";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User create(Role role) {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.save(User.builder()
                .username(username)
                .fullName("Test " + role)
                .email(username + "@example.com")
                .roleId(role.getId())
                .hashedPassword(passwordEncoder.encode(PASSWORD))
                .build());
    }

    public static String login(MockMvc mockMvc, String username, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"deviceId\":\"" + DEVICE + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
