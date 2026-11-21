package com.mouhin.family.tree.domain.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 族内算法与跨族链接正交性的硬性断言。
 *
 * <p>跨族方案册的核心不变量：{@code family_node_link} 只做族间身份引用，
 * 建树 / 世代修复 / 级联同步 / 亲属路径 / GEDCOM / 版本控制等族内算法
 * 永远不得读取或持有跨族链接模型。任何给下列类注入 CrossFamilyLink* 的改动都会使本测试失败，
 * 强制改动者重新评审正交边界。</p>
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
class FamilyIntraAlgorithmsNeverReadLinksTest {

    private static final String FORBIDDEN_TYPE = "CrossFamilyLink";

    @ParameterizedTest
    @ValueSource(strings = {
            "com.mouhin.family.tree.domain.service.FamilyTreeDomainService",
            "com.mouhin.family.tree.domain.service.FamilyNodeDomainService",
            "com.mouhin.family.tree.domain.service.GenerationRepairDomainService",
            "com.mouhin.family.tree.domain.service.RelationPathDomainService",
            "com.mouhin.family.tree.domain.service.RelationValidationDomainService",
            "com.mouhin.family.tree.domain.service.GedcomParserDomainService",
            "com.mouhin.family.tree.domain.service.GedcomGeneratorDomainService",
            "com.mouhin.family.tree.domain.service.VersionControlDomainService",
            "com.mouhin.family.tree.domain.repository.FamilyNodeRepository",
            "com.mouhin.family.tree.domain.repository.FamilyRelationRepository"
    })
    void intraAlgorithmClasses_haveNoCrossFamilyLinkDependency(String className) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className);
        for (Field field : clazz.getDeclaredFields()) {
            assertFalse(genericString(field.getGenericType()).contains(FORBIDDEN_TYPE),
                    className + " must not hold field " + field.getName() + " of cross-family link type");
        }
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                assertFalse(parameterType.getSimpleName().contains(FORBIDDEN_TYPE),
                        className + " must not take cross-family link dependency in constructor");
            }
        }
        for (Method method : clazz.getDeclaredMethods()) {
            assertFalse(method.toGenericString().contains(FORBIDDEN_TYPE),
                    className + " must not expose cross-family link type in method " + method.getName());
        }
    }

    private String genericString(java.lang.reflect.Type type) {
        return type.getTypeName();
    }
}
