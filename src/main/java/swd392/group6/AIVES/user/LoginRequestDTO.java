package swd392.group6.AIVES.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequestDTO {

    @NotBlank(message = "Username is required")
    @Size(max = 50, message = "Username cannot exceed 50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    /** Random id kept by the browser; the same browser re-logging in is not asked to sign out "the other device". */
    @Size(max = 64)
    private String deviceId;

    /** The user confirmed signing out the session on the other device (D38). */
    private Boolean force;
}
