package com.training.fraudcase.tools.dto;

import java.util.List;

public record PriorDisputes(
        int count,
        int similarCount,
        int lookbackDays,
        List<String> observations
) {}
