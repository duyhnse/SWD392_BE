package swd392.group6.AIVES.user;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class UserService {

    private UserRepository userRepository;

    public User getUser(UUID id) {
        return userRepository.getUserByUserId(id);
    }


}
