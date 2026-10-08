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
                .createdAt(user.getCreatedAt())
                .build();
    }
}
