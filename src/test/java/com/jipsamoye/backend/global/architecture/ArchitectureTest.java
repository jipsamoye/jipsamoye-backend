package com.jipsamoye.backend.global.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.jipsamoye.backend");
    }

    // ── 규칙 1: 레이어 의존성 방향 강제 ──

    @Test
    @DisplayName("Controller는 Repository를 직접 참조할 수 없습니다. Service를 통해 접근하세요.")
    void controller_should_not_depend_on_repository() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("Controller는 Repository를 직접 참조할 수 없습니다. Service를 통해 접근하세요.");

        rule.check(classes);
    }

    @Test
    @DisplayName("Service는 Controller를 참조할 수 없습니다. 의존성 방향은 Controller → Service입니다.")
    void service_should_not_depend_on_controller() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("Service는 Controller를 참조할 수 없습니다. 의존성 방향은 Controller → Service입니다.");

        rule.check(classes);
    }

    @Test
    @DisplayName("Repository는 Controller를 참조할 수 없습니다.")
    void repository_should_not_depend_on_controller() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("Repository는 Controller를 참조할 수 없습니다. 의존성 방향은 Controller → Service → Repository입니다.");

        rule.check(classes);
    }

    @Test
    @DisplayName("Entity는 Service나 Controller를 참조할 수 없습니다.")
    void entity_should_not_depend_on_service_or_controller() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..entity..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..")
                .because("Entity는 Service나 Controller를 참조할 수 없습니다. Entity는 순수한 도메인 모델이어야 합니다.");

        rule.check(classes);
    }

    // ── 규칙 2: 도메인 간 순환 참조 금지 ──

    @Test
    @Disabled("알려진 기술 부채: user↔follow, user↔petPost 순환 참조. docs/QUALITY.md 참조")
    @DisplayName("도메인 간 순환 참조가 발견되었습니다. 의존성 방향을 확인하세요.")
    void domain_should_be_free_of_cycles() {
        ArchRule rule = slices()
                .matching("com.jipsamoye.backend.domain.(*)..")
                .should().beFreeOfCycles()
                .because("도메인 간 순환 참조가 발견되었습니다. 의존성 방향을 확인하세요.");

        rule.check(classes);
    }

    // ── 규칙 3: 엔티티 @Setter 금지 ──

    @Test
    @DisplayName("엔티티에 @Setter 사용이 금지되어 있습니다. 상태 변경은 메서드를 통해 수행하세요.")
    void entity_should_not_use_setter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..entity..")
                .should().beAnnotatedWith("lombok.Setter")
                .because("엔티티에 @Setter 사용이 금지되어 있습니다. 상태 변경은 메서드를 통해 수행하세요.");

        rule.check(classes);
    }

    // ── 규칙 4: Controller 어노테이션 강제 ──

    @Test
    @DisplayName("Controller 패키지의 클래스는 @RestController 또는 @Controller 어노테이션이 필요합니다.")
    void controller_should_be_annotated() {
        ArchRule rule = classes()
                .that().resideInAPackage("..controller..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .orShould().beAnnotatedWith(org.springframework.stereotype.Controller.class)
                .because("Controller 패키지의 클래스는 @RestController 또는 @Controller 어노테이션이 필요합니다.");

        rule.check(classes);
    }

    // ── 규칙 5: DTO 위치 강제 ──

    @Test
    @DisplayName("DTO 클래스는 dto/request/ 또는 dto/response/ 패키지에 위치해야 합니다.")
    void dto_should_reside_in_proper_package() {
        ArchRule rule = classes()
                .that().resideInAPackage("..dto..")
                .and().haveSimpleNameEndingWith("Request")
                .should().resideInAPackage("..dto.request..")
                .because("Request DTO 클래스는 dto/request/ 패키지에 위치해야 합니다.");

        ArchRule rule2 = classes()
                .that().resideInAPackage("..dto..")
                .and().haveSimpleNameEndingWith("Response")
                .should().resideInAPackage("..dto.response..")
                .because("Response DTO 클래스는 dto/response/ 패키지에 위치해야 합니다.");

        rule.check(classes);
        rule2.check(classes);
    }
}
