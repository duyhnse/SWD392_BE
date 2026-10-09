package swd392.group6.AIVES.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import swd392.group6.AIVES.common.Language;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {

    private UUID userId;
    private Short roleId;
    private Role role;
    private String username;
    private String fullName;
    private String email;
    private String studentCode;
    private Language preferredLanguage;
    /** Whether a Google account is linked (D25); the Google subject itself is never exposed. */
    private boolean googleLinked;
    private String googleEmail;
    /** Public picture URL with a cache-busting version, or null when the user has no avatar (D33). */
    private String avatarUrl;
    private boolean active;
    private Instant createdAt;

    public static UserResponseDTO fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .roleId(user.getRoleId())
                .role(user.getRole())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .studentCode(user.getStudentCode())
                .preferredLanguage(user.getPreferredLanguage())
                .googleLinked(user.getGoogleSubject() != null)
                .googleEmail(user.getGoogleEmail())
                .avatarUrl(avatarUrl(user))
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    static String avatarUrl(User user) {
        if (user.getAvatarKey() == null || user.getUserId() == null) {
            return null;
        }
        long version = user.getAvatarUpdatedAt() == null ? 0 : user.getAvatarUpdatedAt().getEpochSecond();
        return "/api/v1/avatars/" + user.getUserId() + "?v=" + version;
    }
}
