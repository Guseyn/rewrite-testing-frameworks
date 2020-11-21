/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junitassertj;

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

/**
 * This is a refactoring visitor that will convert JUnit-style assertFalse() to assertJ's assertThat().isFalse().
 *
 * This visitor only supports the migration of the following JUnit 5 assertFalse() methods:
 *
 * <PRE>
 *     assertFalse(boolean condition) -> assertThat(condition).isFalse()
 *     assertFalse(boolean condition, String message) -> assertThat(condition).withFailMessage(message).isFalse();
 *     assertFalse(boolean condition, Supplier<String> messageSupplier) -> assertThat(condition).withFailMessage(messageSupplier).isFalse();
 * </PRE>
 *
 * Note: There are three additional method signatures in JUnit that use a BooleanSupplier for the condition. Attempts
 *       to map these signatures into assertJ's model obfuscates the original assertion. It would be possible to use a
 *       shim method to support these method signatures, however, those shims would need to exist on each compilation
 *       unit or in a shared testing utilities library.
 */
@AutoConfigure
public class AssertFalseToAssertThat extends JavaIsoRefactorVisitor {

    private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS = "org.junit.jupiter.api.Assertions";
    private static final String JUNIT_ASSERT_FALSE_METHOD = "assertFalse";

    private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS = "org.assertj.core.api.Assertions";
    private static final String ASSERTJ_QUALIFIED_BOOLEAN_ASSERT_CLASS = "org.assertj.core.api.AbstractBooleanAssert";
    private static final String ASSERTJ_ASSERT_THAT_METHOD = "assertThat";

    /**
     * This matcher uses a pointcut expression to find the matching junit methods that will be migrated by this visitor
     */
    private static final MethodMatcher assertFalseMatcher = new MethodMatcher(
            JUNIT_QUALIFIED_ASSERTIONS_CLASS + " " + JUNIT_ASSERT_FALSE_METHOD + "(boolean, ..)"
    );
    private static final JavaType.Method assertThatMethodDeclaration;

    static {
        JavaType.Method.Signature booleanAssertThatMethod = new JavaType.Method.Signature(
                JavaType.Class.build(ASSERTJ_QUALIFIED_BOOLEAN_ASSERT_CLASS),
                Collections.singletonList(JavaType.Primitive.Boolean)
        );
        assertThatMethodDeclaration = JavaType.Method.build(
                JavaType.Class.build(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS),
                ASSERTJ_ASSERT_THAT_METHOD,
                booleanAssertThatMethod,
                booleanAssertThatMethod,
                Collections.singletonList("arg1"),
                Stream.of(Flag.Public,Flag.Static).collect(Collectors.toSet()));
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu) {

        maybeRemoveImport(JUNIT_QUALIFIED_ASSERTIONS_CLASS);
        return super.visitCompilationUnit(cu);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation original = super.visitMethodInvocation(method);
        if (!assertFalseMatcher.matches(method)) {
            return original;
        }

        List<Expression> originalArgs = original.getArgs().getArgs();
        Expression condition = originalArgs.get(0);
        Expression message = originalArgs.size() == 2 ? originalArgs.get(1):null;

        //This create the `assertThat(<EXPRESSION>)` method invocation. The type information for this invocation
        //is used to link this reference to the AddImport visitor that adds the static import for "assertThat"
        J.MethodInvocation assertSelect = new J.MethodInvocation(
                randomId(),
                null,
                null,
                J.Ident.build(randomId(), ASSERTJ_ASSERT_THAT_METHOD, JavaType.Primitive.Void, EMPTY),
                new J.MethodInvocation.Arguments(
                        randomId(),
                        Collections.singletonList(condition),
                        EMPTY
                ),
                assertThatMethodDeclaration,
                EMPTY
        );
        if (message != null) {
            //If the assertFalse is the two-argument variant, we need to maintain the message via a chained method
            //call to "withFailMessage". The message may be a String or Supplier<String> and withFailMessage has
            //overloads for both types.
            assertSelect = new J.MethodInvocation(
                    randomId(),
                    assertSelect, //assertThat is the select for this method.
                    null,
                    J.Ident.build(randomId(), "withFailMessage", null, EMPTY),
                    new J.MethodInvocation.Arguments(
                            randomId(),
                            Collections.singletonList(message.withPrefix("")),
                            EMPTY
                    ),
                    null,
                    EMPTY
            );
        }

        //The method that will always be returned is the "isFalse()" method using the assertSelect as the select.
        //One argument form : assertThat(<CONDITION>).isFalse();
        //Two argument form : assertThat(<CONDITION>).withFailMessage(<MESSAGE>).isFalse();
        J.MethodInvocation replacement = new J.MethodInvocation(
                randomId(),
                assertSelect,
                null,
                J.Ident.build(randomId(), "isFalse", JavaType.Primitive.Boolean, EMPTY),
                new J.MethodInvocation.Arguments(
                        randomId(),
                        Collections.emptyList(),
                        EMPTY
                ),
                null,
                format("\n")
        );

        //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat"
        maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS, ASSERTJ_ASSERT_THAT_METHOD);

        //Format the replacement method invocation in the context of where it is called.
        andThen(new AutoFormat(replacement));
        return replacement;
    }
}
