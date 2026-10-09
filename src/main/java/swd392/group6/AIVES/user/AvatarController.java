package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.storage.StoredObject;

import java.time.Duration;
import java.util.UUID;

/** Public profile pictures so a plain {@code <img>} works without a token (D33; permitted in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/avatars")
@RequiredArgsConstructor
class AvatarController {

    private final AvatarService avatarService;

    @GetMapping("/{userId}")
    public ResponseEntity<byte[]> avatar(@PathVariable UUID userId) {
        StoredObject image = avatarService.fetch(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(image.data());
    }
}
