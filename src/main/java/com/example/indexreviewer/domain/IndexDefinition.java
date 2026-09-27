package com.example.indexreviewer.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Business parameters of an index, as configured in {@code config/indices.yml}. The constructor rejects
 * inconsistent values, so a bad configuration fails at startup rather than during a review.
 *
 * @param name                 index name, also the name of its input folder, e.g. {@code SMI}
 * @param methodology          rulebook version and section the index follows, recorded in every report
 * @param universe             universe the constituents are selected from, e.g. {@code SPI}
 * @param constituentCount     number of constituents
 * @param directSelectionRank  securities ranked up to here are selected directly
 * @param bufferEndRank        last rank of the selection buffer; incumbents ranked below it leave
 * @param weightCap            maximum weight of one constituent, in (0, 1]
 * @param rankingStrategy      name of the ranking criterion, e.g. {@code FFMCAP}
 * @param reviewPeriods        scheduled reviews, with unique ids
 */
public record IndexDefinition(
        String name,
        String methodology,
        String universe,
        int constituentCount,
        int directSelectionRank,
        int bufferEndRank,
        BigDecimal weightCap,
        String rankingStrategy,
        List<ReviewPeriod> reviewPeriods) {

    public IndexDefinition {
        Validation.requireText(name, "index name");
        Validation.requireText(methodology, "methodology of " + name);
        Validation.requireText(universe, "universe of " + name);
        Validation.requireText(rankingStrategy, "ranking strategy of " + name);
        Validation.require(constituentCount > 0,
                "%s: constituent count %d must be positive".formatted(name, constituentCount));
        Validation.require(directSelectionRank >= 1 && directSelectionRank <= constituentCount,
                "%s: direct selection rank %d must be between 1 and the constituent count %d"
                        .formatted(name, directSelectionRank, constituentCount));
        Validation.require(bufferEndRank >= constituentCount,
                "%s: buffer end rank %d must not be below the constituent count %d"
                        .formatted(name, bufferEndRank, constituentCount));
        Objects.requireNonNull(weightCap, "weight cap of " + name);
        Validation.require(weightCap.signum() > 0 && weightCap.compareTo(BigDecimal.ONE) <= 0,
                "%s: weight cap %s must be in (0, 1]".formatted(name, weightCap));
        // With n constituents each capped at c, the weights can only add up to 100% if n × c ≥ 1.
        Validation.require(weightCap.multiply(BigDecimal.valueOf(constituentCount)).compareTo(BigDecimal.ONE) >= 0,
                "%s: %d constituents capped at %s cannot add up to 100%%"
                        .formatted(name, constituentCount, weightCap));
        reviewPeriods = List.copyOf(reviewPeriods == null ? List.of() : reviewPeriods);
        var ids = new HashSet<String>();
        for (var period : reviewPeriods) {
            Validation.require(ids.add(period.id()), "%s: duplicate review period %s".formatted(name, period.id()));
        }
    }

    public Optional<ReviewPeriod> reviewPeriod(String id) {
        return reviewPeriods.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
