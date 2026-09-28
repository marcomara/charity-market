package it.charitymarket.user;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
public record CreateUserRequest(
        @NotBlank
        @Size(max = 100)
        String username,

        @NotBlank
        @Size(max = 200)
        String displayName,

        @Email
        @Size(max = 320)
        String email,

        @NotBlank
        @Size(min = 8, max = 128)
        String temporaryPassword,

        @NotEmpty
        Set<UserRole> roles
) {
}
