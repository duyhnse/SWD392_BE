package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.storage.StoredObject;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

/**
 * Profile pictures (D33): PNG/JPEG/WebP up to 2 MB, recognised by their magic bytes (the client's
 * Content-Type is not trusted), stored through {@link StoragePort} at {@code avatars/{userId}/{random}.{ext}}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class AvatarService {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private final UserRepository userRepository;
    private final StoragePort storage;
    private final Clock clock;

    /** Stores the new picture, then removes the previous object. Returns the user's new avatar URL. */
    @Transactional
    public String replace(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.unprocessable("AVATAR_EMPTY", "Choose an image file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw tooLarge();
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.unprocessable("AVATAR_EMPTY", "The uploaded file could not be read");
        }
        if (data.length > MAX_BYTES) {
            throw tooLarge();
        }
        ImageType type = ImageType.sniff(data);
        if (type == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "AVATAR_UNSUPPORTED_TYPE",
                    "Only PNG, JPEG or WebP images are accepted");
        }

        User user = load(userId);
        String previous = user.getAvatarKey();
        String key = "avatars/" + userId + "/" + UUID.randomUUID().toString().replace("-", "") + "." + type.extension;
        storage.put(key, data, type.contentType);
        user.setAvatarKey(key);
        user.setAvatarUpdatedAt(clock.instant());
        userRepository.saveAndFlush(user);
        deleteQuietly(previous);
        return UserResponseDTO.avatarUrl(user);
    }

    @Transactional
    public void remove(UUID userId) {
        User user = load(userId);
        String previous = user.getAvatarKey();
        user.setAvatarKey(null);
        user.setAvatarUpdatedAt(clock.instant());
        userRepository.saveAndFlush(user);
        deleteQuietly(previous);
    }

    @Transactional(readOnly = true)
    public StoredObject fetch(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getAvatarKey)
                .flatMap(storage::get)
                .orElseThrow(() -> ApiException.notFound("AVATAR_NOT_FOUND", "This user has no profile picture"));
    }

    void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            // An orphaned file is harmless; the database no longer points to it.
            log.warn("Could not delete old avatar object {}", key, e);
        }
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    private static ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AVATAR_TOO_LARGE", "The picture must be at most 2 MB");
    }

    enum ImageType {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp");

        final String extension;
        final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        static ImageType sniff(byte[] d) {
            if (startsWith(d, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
                return PNG;
            }
            if (startsWith(d, 0, 0xFF, 0xD8, 0xFF)) {
                return JPEG;
            }
            if (startsWith(d, 0, 'R', 'I', 'F', 'F') && startsWith(d, 8, 'W', 'E', 'B', 'P')) {
                return WEBP;
            }
            return null;
        }

        private static boolean startsWith(byte[] data, int offset, int... expected) {
            if (data.length < offset + expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if ((data[offset + i] & 0xFF) != expected[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
