package com.amalitech.todo.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for creating or updating a task. Bean-validated at the controller.
 */
public record TaskRequest(

        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be 255 characters or fewer")
        String title,

        @Size(max = 10_000, message = "description must be 10000 characters or fewer")
        String description,

        boolean completed
) {
}
