package com.example.indexreviewer.config;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.report.ReportFormat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IndexReviewerPropertiesTest {

    private static final IndexDefinition SMI = new IndexDefinition("SMI", "Rulebook v3.40", "SPI", 20, 18, 22,
            new BigDecimal("0.18"), "FFMCAP", List.of());

    @Test
    void rejectsDuplicateIndexNames() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(List.of(SMI, SMI)))
                .withMessage("Duplicate index SMI");
    }

    @Test
    void indicesMayBeLeftOut() {
        assertThat(properties(null).indices()).isEmpty();
    }

    private static IndexReviewerProperties properties(List<IndexDefinition> indices) {
        return new IndexReviewerProperties(Path.of("data"), Path.of("reports"), new ReportFormat(6, 10, 2), indices);
    }
}
