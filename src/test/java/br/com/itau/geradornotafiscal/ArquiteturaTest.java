package br.com.itau.geradornotafiscal;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Regras de dependência entre camadas do ADR-0003 (F03-NF-01), conferidas no build.
 */
@AnalyzeClasses(packages = "br.com.itau.geradornotafiscal", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule f03Nf01_dominioSoDependeDeJavaDoProprioDominioDeEstereotiposEDoLombok = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "br.com.itau.geradornotafiscal.domain..", "org.springframework.stereotype..", "lombok..")
            .because("o domínio não depende de framework, HTTP, JSON nem de outras camadas; só declara beans e usa o Lombok,"
                    + " que gera código na compilação (ADR-0003)");

    @ArchTest
    static final ArchRule f03Nf01_aplicacaoNaoConheceAdaptadoresNemConfiguracao = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..config..")
            .because("a aplicação define as portas e não conhece as implementações (ADR-0003)");

    @ArchTest
    static final ArchRule f03Nf01_entradaNaoDependeDaSaida = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

    @ArchTest
    static final ArchRule f03Nf01_saidaNaoDependeDaEntrada = noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in..");

    @ArchTest
    static final ArchRule f03Nf01_integracoesNaoDependemUmasDasOutras = slices()
            .matching("..adapter.out.(*)..")
            .should().notDependOnEachOther();
}
