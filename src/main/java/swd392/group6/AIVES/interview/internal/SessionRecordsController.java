package swd392.group6.AIVES.interview.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.storage.StoredObject;
import swd392.group6.AIVES.user.User;

import java.util.List;
import java.util.UUID;

/** Lượt thi records (15 §5.4): turns, audit events, audio. The live runtime endpoints arrive with WF3. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class SessionRecordsController {

    private final SessionRecordsService service;

    @GetMapping("/sessions/{id}/turns")
    public List<SessionRecordDtos.TurnDto> turns(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.turns(id, user);
    }

    @GetMapping("/sessions/{id}/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public List<SessionRecordDtos.EventDto> events(@PathVariable UUID id, @AuthenticationPrincipal User user) {
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

    private static ResponseEntity<byte[]> audio(StoredObject object, String fallbackType) {
        String type = object.contentType() == null || object.contentType().isBlank() ? fallbackType : object.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .cacheControl(CacheControl.noStore())
                .body(object.data());
    }
}
