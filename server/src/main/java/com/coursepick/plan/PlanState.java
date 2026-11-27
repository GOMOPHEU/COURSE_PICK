package com.coursepick.plan;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PlanState(
        int courseStartMin,
        int clockMin,
        int endMin,
        String lastTheme,
        int lastCafeClockMin,
        Map<String, Integer> themeCounts,
        Set<String> mealsDone,
        List<String> userThemes) {
}
