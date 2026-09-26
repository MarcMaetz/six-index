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

    /** Dependencies point inwards: HTTP → use cases → wiring → pipeline → domain. */
    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("api").definedBy(ROOT + ".api..")
            .layer("service").definedBy(ROOT + ".service..")
            .layer("config").definedBy(ROOT + ".config..")
            .layer("store").definedBy(ROOT + ".store..")
            .layer("report").definedBy(ROOT + ".report..")
            .layer("review").definedBy(ROOT + ".review..")
            .layer("ingest").definedBy(ROOT + ".ingest..")
            .layer("domain").definedBy(ROOT + ".domain..")
            .whereLayer("api").mayNotBeAccessedByAnyLayer()
            .whereLayer("service").mayOnlyBeAccessedByLayers("api")
            .whereLayer("config").mayOnlyBeAccessedByLayers("service", "api")
            .whereLayer("store").mayOnlyBeAccessedByLayers("config", "service", "api")
            .whereLayer("report").mayOnlyBeAccessedByLayers("store", "config", "service", "api")
            .whereLayer("review").mayOnlyBeAccessedByLayers("report", "store", "config", "service", "api")
            .whereLayer("ingest").mayOnlyBeAccessedByLayers("config", "service", "api");

    /** The review logic and its model are plain Java: no framework or library at all. */
    @ArchTest
    static final ArchRule reviewLogicIsPlainJava = classes()
            .that().resideInAnyPackage(ROOT + ".domain..", ROOT + ".review..", ROOT + ".report..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + "..");

    /** Ingest and store use one library each (OpenCSV, Jackson) but no Spring, so they run without a context. */
    @ArchTest
    static final ArchRule ingestAndStoreAreFrameworkFree = noClasses()
            .that().resideInAnyPackage(ROOT + ".ingest..", ROOT + ".store..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule noPackageCycles = slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();
}
