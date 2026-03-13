package com.soyunju.logcollector.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayerDependencyTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.soyunju.logcollector");

    @Test
    void LC_레이어는_KB_서비스를_직접_참조하면_안된다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service.lc..")
                .should().dependOnClassesThat()
                .resideInAPackage("..service.kb..");

        rule.check(classes);
    }

    @Test
    void LC_레이어는_KB_Repository를_직접_참조하면_안된다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service.lc..")
                .should().dependOnClassesThat()
                .resideInAPackage("..repository.kb..");

        rule.check(classes);
    }

    @Test
    void LC_레이어는_KB_Domain을_직접_참조하면_안된다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service.lc..")
                .should().dependOnClassesThat()
                .resideInAPackage("..domain.kb..");

        rule.check(classes);
    }

    @Test
    void 이벤트_클래스는_LC_KB_어느쪽도_직접_참조하면_안된다() {
        // event 패키지는 순수 DTO여야 함 (양쪽 다 의존하면 안 됨)
        ArchRule rule = noClasses()
                .that().resideInAPackage("..dto.event..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..service.lc..", "..service.kb..");

        rule.check(classes);
    }
}