package swd392.group6.AIVES.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.support.ExamRows;
import swd392.group6.AIVES.support.TestUsers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** SQL fixtures for the FG7 CRUD tests: rows owned by other modules are inserted directly. */
final class CrudFixtures {

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    CrudFixtures(JdbcTemplate jdbc, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    User user(Role role, String username) {
        return userRepository.save(User.builder()
                .username(username)
                .fullName("Fixture " + role)
                .email(username + "@example.com")
                .roleId(role.getId())
                .studentCode(role == Role.STUDENT ? "C" + unique().toUpperCase() : null) // MSSV required (D40)
                .hashedPassword(passwordEncoder.encode(TestUsers.PASSWORD))
                .build());
    }

    static String bearer(MockMvc mockMvc, User user) throws Exception {
        return "Bearer " + TestUsers.login(mockMvc, user.getUsername(), TestUsers.PASSWORD);
    }

    UUID course(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into courses (course_id, code, name) values (?, ?, ?)", id, code, "Course " + code);
        return id;
    }

    void assign(UUID courseId, UUID lecturerId) {
        jdbc.update("insert into course_lecturers (course_id, lecturer_id) values (?, ?)", courseId, lecturerId);
    }

    UUID topic(UUID courseId, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into topics (topic_id, course_id, name, created_by) values (?, ?, ?, ?)",
                id, courseId, "Topic " + unique(), createdBy);
        return id;
    }

    /** One buổi thi with one lượt thi (attempt) for the student. */
    UUID examSession(UUID courseId, UUID lecturerId, UUID studentId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID exam = ExamRows.exam(jdbc, courseId, lecturerId, 3, now, now.plus(1, ChronoUnit.DAYS), "OPEN");
        return ExamRows.attempt(jdbc, exam, studentId, "IN_PROGRESS", now, null);
    }

    /** Bytes that only look like a PNG (magic number + zeros) — for size checks that happen before decoding. */
    static byte[] fakePng(int size) {
        byte[] data = new byte[size];
        byte[] magic = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(magic, 0, data, 0, magic.length);
        return data;
    }

    /** A real image of the given size, painted in three vertical bands: red | green | blue. */
    static byte[] image(String format, int width, int height) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            int rgb = x < width / 4 ? 0xFF0000 : x >= width - width / 4 ? 0x0000FF : 0x00FF00;
            for (int y = 0; y < height; y++) {
                img.setRGB(x, y, rgb);
            }
        }
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] png(int width, int height) {
        return image("png", width, height);
    }

    static byte[] webp() {
        try (java.io.InputStream in = CrudFixtures.class.getResourceAsStream("/avatars/sample.webp")) {
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static java.awt.image.BufferedImage decode(byte[] data) {
        try {
            return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
