package org.acs.stuco.backend.news;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record NewsPostRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title cannot be more than 255 characters")
        String title,

        @NotBlank(message = "Content is required")
        String content,

        @Pattern(regexp = "^(https?://.*)?$", message = "Banner URL must be a valid URL if provided")
        String bannerPhotoUrl,

        @Pattern(regexp = "^(https?://[^,]*,?)*$", message = "Extra photos must be valid URLs separated by commas")
        String extraPhotos
)
{
}


