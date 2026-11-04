package com.hrm.system.dto.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Public sign-up form. Role and access flags are deliberately absent. */
@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
}
