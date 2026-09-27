package com.example.indexreviewer.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validated input for a review: the universe per date, market data per security and date, the current index
 * composition, the data-quality warnings raised while loading them, and the files they were read from.
 * <p>
 * Collections are copied into unmodifiable, insertion-ordered views, so iteration order (and with it every
 * derived result) is reproducible.
 */
public record InputData(
        Map<LocalDate, Set<String>> universeByDate,
        Map<String, Map<LocalDate, SecurityData>> securityDataById,
        Set<String> currentComposition,
        List<DataQualityWarning> warnings,
        List<InputFile> files) {

    public InputData {
        var universe = new LinkedHashMap<LocalDate, Set<String>>();
        universeByDate.forEach((date, ids) -> universe.put(date, Collections.unmodifiableSet(new LinkedHashSet<>(ids))));
        universeByDate = Collections.unmodifiableMap(universe);

        var securityData = new LinkedHashMap<String, Map<LocalDate, SecurityData>>();
        securityDataById.forEach((id, byDate) -> securityData.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(byDate))));
        securityDataById = Collections.unmodifiableMap(securityData);

        currentComposition = Collections.unmodifiableSet(new LinkedHashSet<>(currentComposition));
        warnings = List.copyOf(warnings);
        files = List.copyOf(files);
    }

    /** Securities in the universe on the given date; empty if the universe has no entries for that date. */
    public Set<String> universe(LocalDate date) {
        return universeByDate.getOrDefault(date, Set.of());
    }

    /** Market data of a security on exactly this date, if delivered; no fallback to an earlier date (A15). */
    public Optional<SecurityData> securityData(String securityId, LocalDate date) {
        return Optional.ofNullable(securityDataById.getOrDefault(securityId, Map.of()).get(date));
    }
}
