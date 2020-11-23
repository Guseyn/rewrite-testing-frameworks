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
package org.openrewrite.java.testing.junitassertj

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class AssertFalseToAssertThatTest: RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
            .classpath("junit", "assertj-core")
            .build()

    override val visitors: Iterable<RefactorVisitor<*>> = listOf(AssertFalseToAssertThat())

    @Test
    fun singleStaticMethodNoMessage() = assertRefactored(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.assertFalse;

                public class A {
 
                    @Test
                    public void test() {
                        assertFalse(notification() != null && notification() > 0);
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification() != null && notification() > 0).isFalse();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun singleStaticMethodWithMessageString() = assertRefactored(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.*;

                public class A {

                    @Test
                    public void test() {
                        assertFalse(notification() != null && notification() > 0, "The notification should be negative");
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification() != null && notification() > 0).withFailMessage("The notification should be negative").isFalse();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun singleStaticMethodWithMessageSupplier() = assertRefactored(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.*;

                public class A {

                    @Test
                    public void test() {
                        assertFalse(notification() != null && notification() > 0, () -> "The notification should be negative");
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification() != null && notification() > 0).withFailMessage(() -> "The notification should be negative").isFalse();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun inlineReference() = assertRefactored(
            before = """
                import org.junit.Test;
 
                public class A {

                    @Test
                    public void test() {
                        org.junit.jupiter.api.Assertions.assertFalse(notification() != null && notification() > 0);
                        org.junit.jupiter.api.Assertions.assertFalse(notification() != null && notification() > 0, "The notification should be negative");
                        org.junit.jupiter.api.Assertions.assertFalse(notification() != null && notification() > 0, () -> "The notification should be negative");
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;

                public class A {
                
                    @Test
                    public void test() {
                        assertThat(notification() != null && notification() > 0).isFalse();
                        assertThat(notification() != null && notification() > 0).withFailMessage("The notification should be negative").isFalse();
                        assertThat(notification() != null && notification() > 0).withFailMessage(() -> "The notification should be negative").isFalse();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun mixedReferences() = assertRefactored(
            before = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.*;
                import static org.junit.jupiter.api.Assertions.assertFalse;

                public class A {

                    @Test
                    public void test() {
                        assertFalse(notification() != null && notification() > 0);
                        org.junit.jupiter.api.Assertions.assertFalse(notification() != null && notification() > 0, "The notification should be negative");
                        assertFalse(notification() != null && notification() > 0, () -> "The notification should be negative");
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.*;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification() != null && notification() > 0).isFalse();
                        assertThat(notification() != null && notification() > 0).withFailMessage("The notification should be negative").isFalse();
                        assertThat(notification() != null && notification() > 0).withFailMessage(() -> "The notification should be negative").isFalse();
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )

    @Test
    fun leaveBooleanSuppliersAlone() = assertRefactored(
            before = """
                import org.junit.Test;

                import static org.junit.jupiter.api.Assertions.assertFalse;

                public class A {

                    @Test
                    public void test() {
                        assertFalse(notification() != null && notification() > 0);
                        assertFalse(notification() != null && notification() > 0, "The notification should be negative");
                        assertFalse(notification() != null && notification() > 0, () -> "The notification should be negative");
                        assertFalse(() -> notification() != null && notification() > 0);
                        assertFalse(() -> notification() != null && notification() > 0, "The notification should be negative");
                        assertFalse(() -> notification() != null && notification() > 0, () -> "The notification should be negative");
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """,
            after = """
                import org.junit.Test;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.junit.jupiter.api.Assertions.assertFalse;

                public class A {

                    @Test
                    public void test() {
                        assertThat(notification() != null && notification() > 0).isFalse();
                        assertThat(notification() != null && notification() > 0).withFailMessage("The notification should be negative").isFalse();
                        assertThat(notification() != null && notification() > 0).withFailMessage(() -> "The notification should be negative").isFalse();
                        assertFalse(() -> notification() != null && notification() > 0);
                        assertFalse(() -> notification() != null && notification() > 0, "The notification should be negative");
                        assertFalse(() -> notification() != null && notification() > 0, () -> "The notification should be negative");
                    }
                    private Integer notification() {
                        return 1;
                    }
                }
            """
    )
}