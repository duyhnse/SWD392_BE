package swd392.group6.AIVES.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {

    private UUID userId;
    private Short roleId;
    private String fullName;
    private String email;
    private LocalDateTime createdAt;

    public static UserResponseDTO fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .roleId(user.getRoleId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
