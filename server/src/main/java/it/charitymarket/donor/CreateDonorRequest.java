package it.charitymarket.donor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDonorRequest (
        @NotBlank(message = "The donor name is required")
        @Size (
                max = 200,
                message = "The donor name cannot exceed 200 characters"
        )
        String name,

        @Email(message = "The email address is invalid")
        @Size(
                max = 320,
                message = "The email cannot exceed 320 characters"
        )
        String email,
        @Size(
                max = 40,
                message = "The phone number cannot exceed 40 characters"
        )
        String phone,

        @Size(
                max = 4000,
                message = "Comments cannot exceed 4000 characters"
        )
        String comments
){
}
