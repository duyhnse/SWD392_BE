package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import swd392.group6.AIVES.security.JwtService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    public static final Short DEFAULT_ROLE_ID = User.ROLE_STUDENT; // 3 (STUDENT)

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponseDTO signUp(SignUpRequestDTO request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }

        Short roleId = request.getRoleId() != null ? request.getRoleId() : DEFAULT_ROLE_ID;

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(normalizedEmail)
                .hashedPassword(passwordEncoder.encode(request.getPassword()))
                .roleId(roleId)
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(savedUser);

        return AuthResponseDTO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .user(UserResponseDTO.fromEntity(savedUser))
                .build();
    }

    public AuthResponseDTO signUp(UserCreateRequestDTO request) {
        SignUpRequestDTO signUpRequest = SignUpRequestDTO.builder()
                .fullName(request.resolveFullName())
                .email(request.getEmail())
                .password(request.getPassword())
                .roleId(request.getRoleId())
                .build();
        return signUp(signUpRequest);
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            normalizedEmail,
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        String token = jwtService.generateToken(user);

        return AuthResponseDTO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .user(UserResponseDTO.fromEntity(user))
                .build();
    }

    public User getUser(UUID id) {
        return userRepository.getUserByUserId(id);
    }

    public UserResponseDTO getUserProfile(UUID id) {
        User user = getUser(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + id);
        }
        return UserResponseDTO.fromEntity(user);
    }
}
