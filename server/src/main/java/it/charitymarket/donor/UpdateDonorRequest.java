package it.charitymarket.donor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDonorRequest(
        @NotBlank
        @Size(max = 200)
        String name,

        @Email
        @Size(max = 320)
        String email,

        @Size(max = 40)
        String phone,

        @Size(max = 4000)
        String comments
) {
}
