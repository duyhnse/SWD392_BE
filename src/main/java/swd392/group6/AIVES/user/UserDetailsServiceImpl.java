package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Lets Spring Security load users by username without the security package depending on this module. */
@Service
@RequiredArgsConstructor
class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
