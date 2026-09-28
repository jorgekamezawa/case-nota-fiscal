package br.com.itau.geradornotafiscal;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Telemetria só nos adaptadores e na configuração (F04-NF-08). O domínio já é coberto pelo {@link ArquiteturaTest}.
 */
@AnalyzeClasses(packages = "br.com.itau.geradornotafiscal", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaObservabilidadeTest {

    @ArchTest
    static final ArchRule f04Nf08_aplicacaoNaoConheceTelemetria = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("io.micrometer..", "io.opentelemetry..",
                    "org.springframework.boot.actuate..", "org.slf4j..", "ch.qos.logback..")
            .because("a telemetria fica nos adaptadores e na configuração (F04-NF-08)");
}
