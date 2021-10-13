package pyjava;

import static pyjava.BasicTests.runTest;

import org.junit.jupiter.api.Test;

class TestDecorators {
    @Test
    void testMultipleDecoratorsOnSameLine() {
        runTest(
            """
            @decorator1 @decorator2 def foo() { ... }
            """,

            """
            @decorator1
            @decorator2
            def foo(): ...
            """
        );
    }

    @Test
    void testNamedExpression() {
        runTest(
            """
            @x := decorator1(args) @decorator2(x)
            def foo() { ... }
            """,

            """
            @x := decorator1(args)
            @decorator2(x)
            def foo(): ...
            """
        );
    }

    @Test
    void testMatrixMultiplyOperatorInParens() {
        runTest(
            """
            @(value1 @ value2)
            def foo() { ... }
            """,

            """
            @(value1 @ value2)
            def foo(): ...
            """
        );
    }

    @Test
    void testMatrixMultiplyOperatorInIndex() {
        runTest(
            """
            @decorator[x @ y]
            def foo() { ... }
            """,

            """
            @decorator[x @ y]
            def foo(): ...
            """
        );
    }

    @Test
    void testMatrixMultiplyOperatorInGeneratorExpr() {
        runTest(
            """
            @decorator(x @ y for x, y in items)
            def foo() { ... }
            """,
        
            """
            @decorator(x @ y for x, y in items)
            def foo(): ...    
            """
        );
    }

    @Test
    void testMatrixMultiplyOperatorInArguments() {
        runTest(
            """
            @decorator(x @ y, x / y)
            def foo() { ... }
            """,
            
            """
            @decorator(x @ y, x / y)
            def foo(): ...
            """
        );
    }

    @Test
    void testMatrixMultiplyOperatorInIfExprCondition() {
        runTest(
            """
            @decorator1 if x @ y else decorator2 @decorator3
            def foo() { ... }
            """,

            """
            @decorator1 if x @ y else decorator2
            @decorator3
            def foo(): ...
            """
        );
    }
}
