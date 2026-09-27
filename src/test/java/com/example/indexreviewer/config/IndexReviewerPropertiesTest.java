package com.example.indexreviewer.config;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.RankingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IndexReviewerPropertiesTest {

    private static final IndexDefinition SMI = new IndexDefinition("SMI", "Rulebook v3.40", "SPI", 20, 18, 22,
            new BigDecimal("0.18"), RankingStrategy.FFMCAP, List.of());

    @Test
    void rejectsDuplicateIndexNames() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(List.of(SMI, SMI)))
                .withMessage("Duplicate index SMI");
    }

    @Test
    void indicesMayBeLeftOut() {
        assertThat(properties(null).indices()).isEmpty();
    }

    @Test
    void unknownRankingStrategyStopsStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(BindProperties.class)
                .withPropertyValues("index-reviewer.data-dir=data", "index-reviewer.reports-dir=reports",
                        "index-reviewer.indices[0].name=SMI", "index-reviewer.indices[0].methodology=v3.40",
                        "index-reviewer.indices[0].universe=SPI", "index-reviewer.indices[0].constituent-count=20",
                        "index-reviewer.indices[0].direct-selection-rank=18",
                        "index-reviewer.indices[0].buffer-end-rank=22", "index-reviewer.indices[0].weight-cap=0.18",
                        "index-reviewer.indices[0].ranking-strategy=TURNOVER")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("TURNOVER"));
    }

    @EnableConfigurationProperties(IndexReviewerProperties.class)
    static class BindProperties {
    }

    private static IndexReviewerProperties properties(List<IndexDefinition> indices) {
        return new IndexReviewerProperties(Path.of("data"), Path.of("reports"), indices);
    }
}
