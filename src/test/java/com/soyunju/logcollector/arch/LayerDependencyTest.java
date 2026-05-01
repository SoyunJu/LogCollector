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
    void collector_레이어는_knowledge_서비스를_직접_참조하면_안된다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..collector.service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..knowledge.service..");

        rule.check(classes);
    }

    @Test
    void collector_레이어는_knowledge_repository를_직접_참조하면_안된다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..collector.service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..knowledge.repository..");

        rule.check(classes);
    }

    @Test
    void collector_레이어는_knowledge_domain을_직접_참조하면_안된다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..collector.service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..knowledge.domain..");

        rule.check(classes);
    }

    @Test
    void collector_레이어는_incident_서비스를_직접_참조하면_안된다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..collector.service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..incident.service..");

        rule.check(classes);
    }

    @Test
    void collector_레이어는_incident_domain을_직접_참조하면_안된다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..collector.service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..incident.domain..");

        rule.check(classes);
    }

    @Test
    void 이벤트_클래스는_bounded_context_서비스를_직접_참조하면_안된다() {
        // global.event 패키지는 순수 DTO여야 함
        ArchRule rule = noClasses()
                .that().resideInAPackage("..global.event..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..collector.service..",
                        "..knowledge.service..",
                        "..incident.service.."
                );

        rule.check(classes);
    }

    @Test
    void knowledge_레이어는_collector_서비스를_직접_참조하면_안된다() {
        // knowledge → collector 단방향 의존 금지 (이벤트로만 소통)
        ArchRule rule = noClasses()
                .that().resideInAPackage("..knowledge.service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..collector.service..");
                // .rule.allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void incident_레이어는_knowledge_서비스를_직접_참조하면_안된다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..incident.service..")
                .and().doNotHaveSimpleName("IncidentService") // 한시적 예외 TODO: ai분석 및 draft 생성 knowledge 로 분리
                .should().dependOnClassesThat()
                .resideInAPackage("..knowledge.service..");
                // .rule.allowEmptyShould(true);

        rule.check(classes);
    }
}