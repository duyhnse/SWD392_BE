package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.security.JwtService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Public self-registration. The role is never taken from the client: everyone starts as STUDENT.
     * Lecturers/admins must be promoted by an administrator.
     */
    @Transactional
    public AuthResponseDTO signUp(SignUpRequestDTO request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .hashedPassword(passwordEncoder.encode(request.getPassword()))
                .roleId(Role.STUDENT.getId())
                .build();

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent sign-ups with the same email: the unique constraint wins.
            throw emailTaken();
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequestDTO request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizeEmail(request.getEmail()), request.getPassword()));
        } catch (AuthenticationException e) {
            // Same message for unknown email and wrong password to avoid account enumeration.
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        return toAuthResponse((User) authentication.getPrincipal());
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserProfile(UUID id) {
        return userRepository.findById(id)
                .map(UserResponseDTO::fromEntity)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found with id: " + id));
    }

    private AuthResponseDTO toAuthResponse(User user) {
        return AuthResponseDTO.builder()
                .token(jwtService.generateToken(user.getEmail(), tokenClaims(user)))
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .user(UserResponseDTO.fromEntity(user))
                .build();
    }

    private static Map<String, Object> tokenClaims(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getUserId().toString());
        claims.put("role", user.getRole().name());
        claims.put("roleId", user.getRoleId());
        claims.put("fullName", user.getFullName());
        return claims;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }
}
