package com.ecom.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.ecom");

    @Test
    void service_layer_must_not_depend_on_web() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..web..");
        rule.check(classes);
    }

    @Test
    void domain_layer_must_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideOutsideOfPackages(
                        "..domain..",
                        "java..",
                        "jakarta.persistence..",
                        "com.ecom.common.web.DomainException");
        rule.check(classes);
    }

    @Test
    void exceptions_in_service_must_extend_common_web() {
        ArchRule rule = classes()
                .that().resideInAPackage("..service..")
                .and().haveSimpleNameEndingWith("Exception")
                .should().beAssignableTo(com.ecom.common.web.DomainException.class);
        rule.check(classes);
    }
}
