package com.coursepick.candidate;

import jakarta.validation.constraints.*;
import java.util.Set;

/** The center is the previous stop (or starting point), not always Anguk station. */
public record CandidateRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotBlank @Pattern(regexp = "food|cafe|walk|history|exhibition|culture|shopping") String theme,
        @Pattern(regexp = "solo|friends|family|couple") String companion,
        @Pattern(regexp = "10s|20s|30s|40s|50s|60s|60plus|all") String ageGroup,
        Boolean withChildren,
        @Size(max = 100) Set<@NotBlank String> excludedIds,
        @Size(max = 100) Set<@NotBlank String> excludedGroupKeys) {
    public Set<String> ids() { return excludedIds == null ? Set.of() : excludedIds; }
    public Set<String> groups() { return excludedGroupKeys == null ? Set.of() : excludedGroupKeys; }
}
