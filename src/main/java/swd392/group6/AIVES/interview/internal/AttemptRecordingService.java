package swd392.group6.AIVES.interview.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamApi;
import swd392.group6.AIVES.exam.ExamApi.AttemptInfo;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.storage.StoredObject;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.User;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Proctoring recordings of an attempt (FG5, D50): the student's browser uploads the camera (video + microphone) or
 * screen recording in chunks of ~30 s; lecturers list and play them. Chunks are immutable once stored.
 */
@Service
@RequiredArgsConstructor
@Transactional
class AttemptRecordingService {

    static final long MAX_CHUNK_BYTES = 30L * 1024 * 1024;
    static final Duration UPLOAD_AFTER_END = Duration.ofMinutes(10);
    static final Set<String> KINDS = Set.of("CAMERA", "SCREEN", "AUDIO");
    private static final Map<String, String> EXTENSIONS = Map.of("video/webm", "webm", "audio/webm", "webm",
            "video/mp4", "mp4", "audio/mp4", "m4a", "audio/ogg", "ogg");

    private final JdbcTemplate jdbc;
    private final ExamApi examApi;
    private final CourseAccessApi courseAccess;
    private final StoragePort storage;
    private final Clock clock;

    AttemptRecordDtos.RecordingDto upload(UUID attemptId, String kind, int chunkIndex, byte[] data, String contentType,
                                          Integer durationMs, Instant clientStartedAt, User student) {
        AttemptInfo attempt = examApi.getAttempt(attemptId)
                .filter(a -> a.studentId().equals(student.getUserId()))
                .orElseThrow(AttemptRecordingService::attemptNotFound);
        String k = kind == null ? "" : kind.toUpperCase(Locale.ROOT);
        if (!KINDS.contains(k)) {
            throw ApiException.unprocessable("RECORDING_KIND_INVALID", "kind must be CAMERA, SCREEN or AUDIO");
        }
        if (chunkIndex < 0 || chunkIndex > 100_000) {
            throw ApiException.unprocessable("RECORDING_CHUNK_INVALID", "chunkIndex must be 0–100000");
        }
        Instant now = clock.instant();
        boolean running = "IN_PROGRESS".equals(attempt.status()) || "INTERRUPTED".equals(attempt.status());
        boolean justEnded = "COMPLETED".equals(attempt.status()) && attempt.endedAt() != null
                && !now.isAfter(attempt.endedAt().plus(UPLOAD_AFTER_END));
        if (!running && !justEnded) {
            throw ApiException.conflict("ATTEMPT_NOT_RECORDING", "Recordings can only be uploaded while the attempt runs");
        }
        if (data.length == 0) {
            throw ApiException.unprocessable("FILE_EMPTY", "The recording chunk is empty");
        }
        if (data.length > MAX_CHUNK_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "RECORDING_TOO_LARGE", "A chunk may be at most 30 MB");
        }
        String type = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(type);
        if (extension == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "RECORDING_TYPE_UNSUPPORTED",
                    "Upload video/webm, audio/webm, video/mp4, audio/mp4 or audio/ogg");
        }
        String sha = sha256(data);
        List<String> existing = jdbc.queryForList(
                "select sha256 from attempt_recordings where attempt_id = ? and kind = ? and chunk_index = ?",
                String.class, attemptId, k, chunkIndex);
        if (!existing.isEmpty()) {
            if (existing.getFirst().equals(sha)) {
                return get(attemptId, k, chunkIndex); // retry of the same upload
            }
            throw ApiException.conflict("RECORDING_CHUNK_EXISTS", "Chunk " + chunkIndex + " was already uploaded");
        }
        String key = "attempts/%s/recordings/%s/%05d.%s".formatted(attemptId, k.toLowerCase(Locale.ROOT), chunkIndex, extension);
        storage.put(key, data, type);
        jdbc.update("""
                        insert into attempt_recordings (recording_id, attempt_id, kind, chunk_index, storage_key, content_type,
                                                        size_bytes, duration_ms, sha256, client_started_at, uploaded_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), attemptId, k, chunkIndex, key, type, (long) data.length, durationMs, sha,
                clientStartedAt == null ? null : Timestamp.from(clientStartedAt), Timestamp.from(now));
        return get(attemptId, k, chunkIndex);
    }

    @Transactional(readOnly = true)
    List<AttemptRecordDtos.RecordingDto> list(UUID attemptId, User user) {
        AttemptInfo attempt = examApi.getAttempt(attemptId).orElseThrow(AttemptRecordingService::attemptNotFound);
        courseAccess.requireRead(attempt.courseId(), user);
        return jdbc.query(SELECT + " where attempt_id = ? order by kind, chunk_index", (rs, i) -> map(rs), attemptId);
    }

    @Transactional(readOnly = true)
    StoredObject file(UUID recordingId, User user) {
        List<Object[]> row = jdbc.query("select attempt_id, storage_key from attempt_recordings where recording_id = ?",
                (rs, i) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2)}, recordingId);
        if (row.isEmpty()) {
            throw recordingNotFound();
        }
        AttemptInfo attempt = examApi.getAttempt((UUID) row.getFirst()[0]).orElseThrow(AttemptRecordingService::recordingNotFound);
        try {
            courseAccess.requireRead(attempt.courseId(), user);
        } catch (ApiException e) {
            throw recordingNotFound();
        }
        return storage.get((String) row.getFirst()[1]).orElseThrow(AttemptRecordingService::recordingNotFound);
    }

    private static final String SELECT = """
            select recording_id, kind, chunk_index, content_type, size_bytes, duration_ms, sha256, client_started_at,
                   uploaded_at from attempt_recordings""";

    private AttemptRecordDtos.RecordingDto get(UUID attemptId, String kind, int chunkIndex) {
        return jdbc.query(SELECT + " where attempt_id = ? and kind = ? and chunk_index = ?", (rs, i) -> map(rs),
                attemptId, kind, chunkIndex).getFirst();
    }

    private static AttemptRecordDtos.RecordingDto map(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("recording_id", UUID.class);
        return new AttemptRecordDtos.RecordingDto(id, rs.getString("kind"), rs.getInt("chunk_index"),
                rs.getString("content_type"), rs.getLong("size_bytes"), (Integer) rs.getObject("duration_ms"),
                rs.getString("sha256"), instant(rs.getTimestamp("client_started_at")),
                instant(rs.getTimestamp("uploaded_at")), "/api/v1/recordings/" + id + "/file");
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException attemptNotFound() {
        return ApiException.notFound("ATTEMPT_NOT_FOUND", "Attempt not found");
    }

    private static ApiException recordingNotFound() {
        return ApiException.notFound("RECORDING_NOT_FOUND", "Recording not found");
    }
}
