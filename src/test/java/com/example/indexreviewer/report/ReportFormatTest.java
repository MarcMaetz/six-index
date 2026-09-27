package com.example.indexreviewer.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ReportFormatTest {

    @Test
    void rejectsNegativeDecimals() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReportFormat(-1, 10, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> new ReportFormat(6, -1, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> new ReportFormat(6, 10, -1));
    }
}
