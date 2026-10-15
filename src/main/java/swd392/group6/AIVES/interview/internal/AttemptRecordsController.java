package swd392.group6.AIVES.interview.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.storage.StoredObject;
import swd392.group6.AIVES.user.User;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lượt thi records (15 §5.4): turns, audit events, audio, proctoring recordings. Runtime endpoints arrive with WF3. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class AttemptRecordsController {

    private final AttemptRecordsService service;
    private final AttemptRecordingService recordings;

    @GetMapping("/attempts/{id}/turns")
    public List<AttemptRecordDtos.TurnDto> turns(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.turns(id, user);
    }

    @GetMapping("/attempts/{id}/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public List<AttemptRecordDtos.EventDto> events(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.events(id, user);
    }

    @GetMapping("/turns/{id}/answer-audio")
    public ResponseEntity<byte[]> answerAudio(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return audio(service.answerAudio(id, user), "audio/webm");
    }

    @GetMapping("/turns/{id}/question-audio")
    public ResponseEntity<byte[]> questionAudio(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return audio(service.questionAudio(id, user), "audio/mpeg");
    }

    /** Student's own proctoring recording, one chunk per call (FG5, D50). */
    @PutMapping("/attempts/{id}/recordings/{kind}/{chunkIndex}")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptRecordDtos.RecordingDto uploadRecording(@PathVariable UUID id, @PathVariable String kind,
                                                          @PathVariable int chunkIndex,
                                                          @RequestPart("file") MultipartFile file,
                                                          @RequestParam(required = false) Integer durationMs,
                                                          @RequestParam(required = false) Instant clientStartedAt,
                                                          @AuthenticationPrincipal User user) throws IOException {
        if (file.getSize() > AttemptRecordingService.MAX_CHUNK_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "RECORDING_TOO_LARGE", "A chunk may be at most 30 MB");
        }
        return recordings.upload(id, kind, chunkIndex, file.getBytes(), file.getContentType(), durationMs,
                clientStartedAt, user);
    }

    @GetMapping("/attempts/{id}/recordings")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public List<AttemptRecordDtos.RecordingDto> recordings(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return recordings.list(id, user);
    }

    @GetMapping("/recordings/{id}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<byte[]> recordingFile(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return audio(recordings.file(id, user), "video/webm");
    }

    private static ResponseEntity<byte[]> audio(StoredObject object, String fallbackType) {
        String type = object.contentType() == null || object.contentType().isBlank() ? fallbackType : object.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .cacheControl(CacheControl.noStore())
                .body(object.data());
    }
}
