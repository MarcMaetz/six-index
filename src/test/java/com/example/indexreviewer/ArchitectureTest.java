package com.example.indexreviewer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The package structure of DESIGN.md as tests: a change that breaks it fails the build instead of
 * relying on review.
 */
@AnalyzeClasses(packages = "com.example.indexreviewer", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.example.indexreviewer";

    /**
     * Dependencies point inwards: HTTP → use cases → pipeline → domain. {@code config} is the Spring
     * wiring, outermost, and nothing depends on it.
     */
    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("config")
            .definedBy(ROOT + ".config..")
            .layer("api")
            .definedBy(ROOT + ".api..")
            .layer("service")
            .definedBy(ROOT + ".service..")
            .layer("store")
            .definedBy(ROOT + ".store..")
            .layer("report")
            .definedBy(ROOT + ".report..")
            .layer("review")
            .definedBy(ROOT + ".review..")
            .layer("ingest")
            .definedBy(ROOT + ".ingest..")
            .layer("domain")
            .definedBy(ROOT + ".domain..")
            .whereLayer("config")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("api")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("service")
            .mayOnlyBeAccessedByLayers("api", "config")
            .whereLayer("store")
            .mayOnlyBeAccessedByLayers("service", "api", "config")
            .whereLayer("report")
            .mayOnlyBeAccessedByLayers("store", "service", "api", "config")
            .whereLayer("review")
            .mayOnlyBeAccessedByLayers("report", "store", "service", "api", "config")
            .whereLayer("ingest")
            .mayOnlyBeAccessedByLayers("service", "api", "config");

    /** The review logic and its model are plain Java: no framework or library at all. */
    @ArchTest
    static final ArchRule reviewLogicIsPlainJava = classes()
            .that()
            .resideInAnyPackage(ROOT + ".domain..", ROOT + ".review..", ROOT + ".report..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", ROOT + "..");

    /**
     * Spring only in the wiring, the use cases and the HTTP layer. Everything else (including {@code ingest} with
     * OpenCSV and {@code store} with Jackson) runs without a context.
     */
    @ArchTest
    static final ArchRule springOnlyInConfigServiceAndApi = noClasses()
            .that()
            .resideOutsideOfPackages(ROOT, ROOT + ".config..", ROOT + ".service..", ROOT + ".api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework..");

    /** The status judges a finished review; the review itself never depends on it (the slices below can't see this). */
    @ArchTest
    static final ArchRule reviewDoesNotDependOnStatus = noClasses()
            .that()
            .resideInAPackage(ROOT + ".review")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ROOT + ".review.status..");

    @ArchTest
    static final ArchRule noPackageCycles =
            slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();
}
