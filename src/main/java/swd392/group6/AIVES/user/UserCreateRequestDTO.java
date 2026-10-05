package swd392.group6.AIVES.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserCreateRequestDTO {
    private String username;
    private String email;
    private String password;
}
