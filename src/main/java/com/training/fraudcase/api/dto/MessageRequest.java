package com.training.fraudcase.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Case-scoped follow-up question (US-05). */
public record MessageRequest(
        @NotBlank @Size(max = 2000) String question
) {}
