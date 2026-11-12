package com.training.fraudcase.api.dto;

import com.training.fraudcase.domain.enums.Disposition;
import com.training.fraudcase.domain.enums.Route;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Reviewer disposition — reason is mandatory (§5.1, US-06). */
public record ReviewRequest(
        @NotNull Disposition disposition,
        @NotBlank @Size(max = 2000) String reason,
        Route selectedRoute
) {}
