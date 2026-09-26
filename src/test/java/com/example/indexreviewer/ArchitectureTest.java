package com.example.indexreviewer;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The package structure of DESIGN.md as tests (D4, D30): a change that breaks it fails the build instead of
 * relying on review.
 */
@AnalyzeClasses(packages = "com.example.indexreviewer", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.example.indexreviewer";

    /**
     * Dependencies point inwards: HTTP → use cases → catalog and pipeline → domain. {@code config} is the Spring
     * wiring, outermost, and nothing depends on it (D32).
     */
    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("config").definedBy(ROOT + ".config..")
            .layer("api").definedBy(ROOT + ".api..")
            .layer("service").definedBy(ROOT + ".service..")
            .layer("catalog").definedBy(ROOT + ".catalog..")
            .layer("store").definedBy(ROOT + ".store..")
            .layer("report").definedBy(ROOT + ".report..")
            .layer("review").definedBy(ROOT + ".review..")
            .layer("ingest").definedBy(ROOT + ".ingest..")
            .layer("domain").definedBy(ROOT + ".domain..")
            .whereLayer("config").mayNotBeAccessedByAnyLayer()
            .whereLayer("api").mayNotBeAccessedByAnyLayer()
            .whereLayer("service").mayOnlyBeAccessedByLayers("api")
            .whereLayer("catalog").mayOnlyBeAccessedByLayers("service", "api", "config")
            .whereLayer("store").mayOnlyBeAccessedByLayers("service", "api", "config")
            .whereLayer("report").mayOnlyBeAccessedByLayers("store", "service", "api", "config")
            .whereLayer("review").mayOnlyBeAccessedByLayers("catalog", "report", "store", "service", "api", "config")
            .whereLayer("ingest").mayOnlyBeAccessedByLayers("service", "api", "config");

    /** The review logic and its model are plain Java: no framework or library at all. */
    @ArchTest
    static final ArchRule reviewLogicIsPlainJava = classes()
            .that().resideInAnyPackage(ROOT + ".domain..", ROOT + ".review..", ROOT + ".report..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + "..");

    /**
     * Spring only in the wiring, the use cases and the HTTP layer. Everything else (including {@code ingest} with
     * OpenCSV and {@code store} with Jackson) runs without a context.
     */
    @ArchTest
    static final ArchRule springOnlyInConfigServiceAndApi = noClasses()
            .that().resideOutsideOfPackages(ROOT, ROOT + ".config..", ROOT + ".service..", ROOT + ".api..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule noPackageCycles = slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();
}
