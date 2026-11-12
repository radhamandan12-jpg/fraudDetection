package com.training.fraudcase.tools.dto;

import java.util.List;

public record ScreeningResult(
        boolean matchFound,
        String listName,
        String matchCategory,
        List<String> observations
) {}
