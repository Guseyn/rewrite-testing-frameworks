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
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

/**
 * This is a refactoring visitor that will convert JUnit-style assertEquals() to assertJ's assertThat().isEqualTo().
 *
 * This visitor has to convert a surprisingly large number (93 methods) of JUnit's assertEquals to assertThat().
 *
 * <PRE>
 *  Two parameter variants:
 *
 *  assertEquals(expected,actual) -> assertThat(actual).isEqualTo(expected)
 *
 *  Three parameter variant where the third argument is either a String or String Supplier:
 *
 *  assertEquals(expected, actual, "message") -> assertThat(actual).withFailureMessage("message").isEqualTo(expected)
 *
 *  Three parameter variant where args are either double or float:
 *
 *  assertEquals(expected, actual, delta) -> assertThat(actual).isCloseTo(expected, within(delta));
 *
 *  Four parameter variant when comparing floating point primitives with a delta and a message:
 *
 *  assertEquals(expected, actual, delta) -> assertThat(actual)withFailureMessage("message").isCloseTo(expected, within(delta));
 *
 * </PRE>
 */
@AutoConfigure
public class AssertEqualsToAssertThat extends JavaIsoRefactorVisitor {

    private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS = "org.assertj.core.api.Assertions";
    private static final String ASSERTJ_ASSERT_THAT_METHOD = "assertThat";
    private static final JavaType ASSERTJ_ASSERT_THAT_STATIC_IMPORT = JavaType.Class.build("org.assertj.core.api.Assertions.assertThat");

    /**
     * This matcher uses a pointcut expression to find the matching junit methods that will be migrated by this visitor
     */
    private static final MethodMatcher assertFalseMatcher = new MethodMatcher(
            JUNIT_QUALIFIED_ASSERTIONS_CLASS + " assertEquals(..)"
    );

    public AssertEqualsToAssertThat() {
        setCursoringOn();
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

        Expression expected = originalArgs.get(0);
        Expression actual = originalArgs.get(1);

        J.MethodInvocation replacement;
        if (originalArgs.size() == 2) {
            //assertThat(actual).isEqualTo(expected)
            replacement = assertSimple(actual, expected);
        } else if (originalArgs.size() == 3 && !isFloatingPointType(originalArgs.get(2))) {
            //assertThat(actual).withFailureMessage(message).isEqualTo(expected)
            replacement = assertWithMessage(actual, expected, originalArgs.get(2));
        } else if (originalArgs.size() == 3) {
            //assert is using primitive floating points with a delta and no message.
            replacement = assertFloatingPointDelta(actual, expected, originalArgs.get(2));
            //The assertEquals is using a primitive floating point with a delta argument. (There may be an optional)
            //fourth argument that contains the message.
        } else {
            replacement = assertFloatingPointDeltaWithMessage(actual, expected, originalArgs.get(2), originalArgs.get(3));
        }

        //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat"
        maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS, ASSERTJ_ASSERT_THAT_METHOD);

        //Format the replacement method invocation in the context of where it is called.
        andThen(new AutoFormat(replacement));
        return replacement;
    }

    private J.MethodInvocation assertSimple(Expression actual, Expression expected) {

        List<J.MethodInvocation> statements = treeBuilder.buildSnippet(getCursor().getParent(),
                String.format("Assertions.assertThat(%s).isEqualTo(%s);", actual.printTrimmed(), expected.printTrimmed()),
                ASSERTJ_ASSERT_THAT_STATIC_IMPORT
        );
        return statements.get(0);
    }

    private J.MethodInvocation assertWithMessage(Expression actual, Expression expected, Expression message) {
        return null;
    }

    private J.MethodInvocation assertFloatingPointDelta(Expression actual, Expression expected, Expression delta) {
        return null;
    }

    private J.MethodInvocation assertFloatingPointDeltaWithMessage(Expression actual, Expression expected,
            Expression delta, Expression message) {
        return null;
    }

    /**
     * Returns true if the expression's type is either a primitive float or double.
     *
     * @param expression The expression parsed from the original AST.
     * @return true if the type is a floating point number.
     */
    private boolean isFloatingPointType(Expression expression) {
        JavaType.Primitive parameterType = TypeUtils.asPrimitive(expression.getType());
        return parameterType == JavaType.Primitive.Double || parameterType == JavaType.Primitive.Float;
    }
}
