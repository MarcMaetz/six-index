package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.domain.SecurityData;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides which universe securities can be ranked: those in the universe on the review date with a price on
 * the cut-off date and shares and free float on the review date. Others are excluded with a reason (A2).
 * <p>
 * These are the data the weights need (FFMCAP, rulebook 5.12.4), whatever the ranking strategy, so the check
 * holds for every strategy; a strategy needing more data would add its own exclusions.
 */
final class Eligibility {

    record Result(List<EligibleSecurity> eligible, List<Exclusion> excluded) {
        Result {
            eligible = List.copyOf(eligible);
            excluded = List.copyOf(excluded);
        }
    }

    private Eligibility() {
    }

    static Result check(InputData data, ReviewPeriod period) {
        var eligible = new ArrayList<EligibleSecurity>();
        var excluded = new ArrayList<Exclusion>();
        for (String id : data.universe(period.reviewDate())) {
            BigDecimal price = data.securityData(id, period.cutOffDate()).map(SecurityData::price).orElse(null);
            var review = data.securityData(id, period.reviewDate());
            Long shares = review.map(SecurityData::shares).orElse(null);
            BigDecimal freeFloat = review.map(SecurityData::freeFloat).orElse(null);

            var missing = new ArrayList<String>();
            if (price == null) {
                missing.add("price on cut-off date " + period.cutOffDate());
            }
            if (shares == null) {
                missing.add("shares on review date " + period.reviewDate());
            }
            if (freeFloat == null) {
                missing.add("free float on review date " + period.reviewDate());
            }
            if (missing.isEmpty()) {
                eligible.add(EligibleSecurity.of(id, price, shares, freeFloat));
            } else {
                excluded.add(new Exclusion(id, "Missing " + String.join(", ", missing)));
            }
        }
        return new Result(eligible, excluded);
    }
}
