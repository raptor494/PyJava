package pyjava;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static pyjava.BasicTests.*;

import java.util.stream.Stream;

import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TestForceParens {
    @Test
    void testParensAroundCompoundStatements() {
        runTest(
            """
            if (condition) {
                doStuff1();
            } elif (condition2) {
                doStuff2();
            } else {
                doStuff3();
            }
            while (condition) {
                doStuff4();
            }
            for (var in exprs) {
                doStuff5();
            }
            with (open(filename) as file) {
                doStuff6();
            }
            try {
                doStuff7();
            } except (Exception as e) {
                doStuff8();
            }
            match (exprs) {
                case 0 {
                    doStuff9();
                }
                case 1 {
                    doStuff10();
                }
            }
            """,
            FORCE_PARENS | REQUIRE_SEMICOLONS,
            """
            if (condition):
                doStuff1()
            elif (condition2):
                doStuff2()
            else:
                doStuff3()
            while (condition):
                doStuff4()
            for var in exprs:
                doStuff5()
            with open(filename) as file:
                doStuff6()
            try:
                doStuff7()
            except Exception as e:
                doStuff8()
            match (exprs):
                case 0:
                    doStuff9()
                case 1:
                    doStuff10()
            """
        );
    }

    @Test
    void testMissingParensAroundIfStatement() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                if condition {
                    doStuff();
                }
                """,
                FORCE_PARENS | REQUIRE_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testMissingParensAroundWhileLoop() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                while condition {
                    doStuff();
                }
                """,
                FORCE_PARENS | REQUIRE_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testMissingParensAroundForLoop() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                for var in exprs {
                    doStuff();
                }
                """,
                FORCE_PARENS | REQUIRE_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testMissingParensAroundWithStatement() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                with open(filename) as file {
                    doStuff();
                }
                """,
                FORCE_PARENS | REQUIRE_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testMissingParensAroundExceptClause() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                try {
                    doStuff();
                } except Exception as e {
                    handleError();
                }
                """,
                FORCE_PARENS | REQUIRE_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testMissingParensAroundAssertStatement() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                assert True
                """,
                FORCE_PARENS | FORCE_PARENS_IN_RETURN | OPTIONAL_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testMissingParensAroundReturnStatement() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                return x
                """,
                FORCE_PARENS | FORCE_PARENS_IN_RETURN | OPTIONAL_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testMissingParensAroundRaiseStatement() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                raise Exception
                """,
                FORCE_PARENS | FORCE_PARENS_IN_RETURN | OPTIONAL_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testMissingParensAroundDelStatement() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                del x.y
                """,
                FORCE_PARENS | FORCE_PARENS_IN_RETURN | OPTIONAL_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testMissingParensAroundYieldStatement() {
        var e = assertThrows(ParseCancellationException.class, () ->
            runTest(
                """
                yield z
                """,
                FORCE_PARENS | FORCE_PARENS_IN_RETURN | OPTIONAL_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), NoViableAltException.class);
    }

    @Test
    void testParensAroundSimpleStatements() {
        // Note: the match statement below is actually
        // testing for parentheses around the case patterns.
        runTest(
            """
            return
            return (x)
            yield
            yield from (y)
            raise
            raise (Exception)
            assert (condition)
            assert (condition, )
            assert (condition, "message")
            del (x.y)
            match (x) {
                case (0) {}
                case (y) {}
            }
            """,
            FORCE_PARENS | FORCE_PARENS_IN_RETURN | OPTIONAL_SEMICOLONS,
            """
            return
            return (x)
            yield
            yield from (y)
            raise
            raise (Exception)
            assert (condition)
            assert (condition)
            assert (condition), ("message")
            del x.y
            match (x):
                case 0: pass
                case y: pass
            """
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssertParens(int flags, String expectedOutput) {
        runTest(
            """
            assert (condition)
            assert (condition,)
            assert (condition, "message")
            """,
            flags,
            expectedOutput
        );
    }

    static Stream<Arguments> testAssertParens() {
        return Stream.of(
            arguments(
                FORCE_PARENS | FORCE_PARENS_IN_RETURN | OPTIONAL_SEMICOLONS,
                """
                assert (condition)
                assert (condition)
                assert (condition), ("message")
                """
            ),
            arguments(
                OPTIONAL_SEMICOLONS,
                """
                assert (condition)
                assert (condition,)
                assert (condition, "message")
                """
            )
        );
    }
}
