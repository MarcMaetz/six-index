package com.example.indexreviewer.config;

import com.example.indexreviewer.report.ReviewReport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** How the build recorded in each report is read from Gradle's generated files (D31). */
class ReviewConfigurationTest {

    private static final BuildProperties BUILD = new BuildProperties(properties("version", "1.4.2"));

    @Test
    void cleanBuildRecordsVersionAndShortCommitId() {
        var git = new GitProperties(properties("commit.id", "97f71f1a2b3c4d5e", "dirty", "false"));

        assertThat(ReviewConfiguration.build(BUILD, git)).isEqualTo(new ReviewReport.Build("1.4.2", "97f71f1"));
    }

    @Test
    void uncommittedChangesAreMarkedDirty() {
        var git = new GitProperties(properties("commit.id", "97f71f1a2b3c4d5e", "dirty", "true"));

        assertThat(ReviewConfiguration.build(BUILD, git).revision()).isEqualTo("97f71f1-dirty");
    }

    @Test
    void missingFilesGiveUnknown() {
        assertThat(ReviewConfiguration.build(null, null)).isEqualTo(ReviewReport.Build.UNKNOWN);
    }

    private static Properties properties(String... keysAndValues) {
        var properties = new Properties();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }
        return properties;
    }
}
