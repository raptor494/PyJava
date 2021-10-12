package pyjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;

import pyjava.parser.PyJavaLexer;
import pyjava.parser.PyJavaParser;
import pyjava.tree.LazyAppendable.AppendFunction;
import pyjava.tree.Transpiler;

class TestPyJava {
    private static final int REQUIRE_SEMICOLONS = 1, OPTIONAL_SEMICOLONS = 0;
    private static final int FORCE_PARENS = 1 << 1;
    private static final int FORCE_PARENS_IN_RETURN = 1 << 2;

    @Test
    void test0() {
        runTest(
            """
            x = 20
            y: list[str] = []
            z: list[str]
            z = None
            """,
            """
            x = 20
            y: list[str] = []
            z: list[str]
            z = None
            """
        );
    }

    @Test
    void test1() {
        var e = assertThrows(ParseCancellationException.class, () -> 
            runTest(
                """
                2 + 3 * 5
                """,
                REQUIRE_SEMICOLONS,
                null
            )
        );
        assertException(e.getCause(), FailedPredicateException.class, "expected semicolon");
    }

    @Test
    void test2() {
        runTest(
            """
            print
            ("Hello, world");
            """,
            OPTIONAL_SEMICOLONS,
            """
            print
            ("Hello, world")
            """
        );
        runTest(
            """
            print
            ("Hello, world");
            """,
            REQUIRE_SEMICOLONS,
            """
            print("Hello, world")
            """
        );
    }

    @Test
    void test3() {
        runTest(
            """
            from random import randrange
            elems = [randrange(10) for _ in range(10)] # Generate 10 random integers from 0 to 9
            elems = list(filter(lambda elem {
                if elem < 10: return True
                if elem == 2 return False;
                  return True
            }, elems));
            print(elems)
            """,
            """
            from random import randrange
            elems = [randrange(10) for _ in range(10)]
            def __lambda0(elem):
                if elem < 10:
                    return True
                if elem == 2:
                    return False
                return True
            elems = list(filter(__lambda0, elems))
            print(elems)
            """
        );
    }

    @Test
    void test4() {
        String output = """
            if (x < 10):
                print(x)
            """;

        runTest(
            """
            if (x < 10) {
                print(x);
            }
            """,
            output
        );
        runTest(
            """
            if (x < 10):
                print(x);
            """,
            output
        );
        runTest(
            """
            if (x < 10): print(x);
            """,
            output
        );
        runTest(
            """
            if (x < 10)
                print(x)
            """,
            output
        );
        runTest(
            """
            if (x < 10) print(x);
            """,
            output
        );
    }

    @Test
    void test5() {
        runTest(
            """
            x = 20
              y = 30
             z = 50
                if (x < 
            z) { print(
                    y, end
                     = ""
                )}
            """,
            """
            x = 20
            y = 30
            z = 50
            if (x < z):
                print(y, end="")
            """
        );
    }

    @Test
    void test6() {
        runTest(
            """
            from abc import ABCMeta, abstractmethod;
            class Animal(metaclass=ABCMeta) {
                @abstractmethod def speak(self) { ... }
                @property
                @abstractmethod def name(self) -> str { ... }
            }
            mouse = class(Animal)("Mouse") {
                def __init__(self, name: str) {
                    self._name = name;
                }

                def speak(self) { print("Squeak!"); }

                @property
                def name(self) -> str { return self._name; }
            };
            print(mouse.name, "says:");
            mouse.speak();
            """,
            """
            from abc import ABCMeta, abstractmethod
            class Animal(metaclass=ABCMeta):
                @abstractmethod
                def speak(self): ...
                @property
                @abstractmethod
                def name(self) -> str: ...
            def __Animal0():
                class __Animal0(Animal):
                    def __init__(self, name: str):
                        self._name = name
                    def speak(self):
                        print("Squeak!")
                    @property
                    def name(self) -> str:
                        return self._name
                return __Animal0
            mouse = __Animal0()("Mouse")
            print(mouse.name, "says:")
            mouse.speak()
            """
        );
    }

    @Test
    void test7() {
        runTest(
            """
            click_counter = 0;
            click_display = document['#clickDisplay'];
            document.add_event_listener('click', lambda (event: EventInfo) {
                nonlocal click_counter;
                x = event.mouse_x;
                y = event.mouse_y;
                if 20 <= x <= 30 and 55 <= y <= 75 {
                    alert("You found a secret button!");
                }
                click_counter += 1;
                click_display.text = f"You have clicked {click_counter} time(s).";
            });
            """,
            """
            click_counter = 0
            click_display = document['#clickDisplay']
            def __lambda0(event: EventInfo):
                nonlocal click_counter
                x = event.mouse_x
                y = event.mouse_y
                if 20 <= x <= 30 and 55 <= y <= 75:
                    alert("You found a secret button!")
                click_counter += 1
                click_display.text = f"You have clicked {click_counter} time(s)."
            document.add_event_listener('click', __lambda0)
            """
        );
    }

    @Test
    void test8() {
        runTest(
            """
            foo(
                lambda {
                    print("Hello, world!");
                    return 5;
                },
                lambda (x): x + 2,
                lambda (x: str, y: int) -> str: x * y,
                lambda (): 0,
                lambda -> str: "Hello, world!"
            )
            """,
            """
            def __lambda0():
                print("Hello, world!")
                return 5
            def __lambda1(x: str, y: int) -> str: return x * y
            def __lambda2() -> str: return "Hello, world!"
            foo(__lambda0, lambda x: x + 2, __lambda1, lambda: 0, __lambda2)
            """
        );
    }

    @Test
    void test9() {
        runTest(
            """
            x if x < 5 else 9
            """,
            """
            x if x < 5 else 9
            """
        );
        runTest(
            """
            x
            if x < 5
            else 9
            """,
            """
            x if x < 5 else 9
            """
        );
    }

    @Test
    void test10() {
        runTest(
            """
            class() { def say_hello(self) { print("Hello!"); } }
                .say_hello();
            """,
            """
            def __object0():
                class __object0:
                    def say_hello(self):
                        print("Hello!")
                return __object0
            __object0()().say_hello()
            """
        );
    }

    @Test
    void test11() {
        runTest(
            """
            from typing import NamedTuple;
            class Point2d(NamedTuple) { x: int; y: int; }
            class Point3d(NamedTuple) { x: int; y: int; z: int; }
            def make_point_3d(pt) {
                match (pt) {
                    case Point3d(_, _, _) {
                        return pt;
                    }
                    case Point2d(x, y) | (x, y) {
                        return Point3d(x, y, 0);
                    }
                    case (x, y, z) {
                        return Point3d(x, y, z);
                    }
                    case _ {
                        raise TypeError("Not a point we support");
                    }
                }
            }
            """,
            REQUIRE_SEMICOLONS,
            """
            from typing import NamedTuple
            class Point2d(NamedTuple):
                x: int
                y: int
            class Point3d(NamedTuple):
                x: int
                y: int
                z: int
            def make_point_3d(pt):
                match (pt):
                    case Point3d(_, _, _):
                        return pt
                    case Point2d(x, y) | (x, y):
                        return Point3d(x, y, 0)
                    case (x, y, z):
                        return Point3d(x, y, z)
                    case _:
                        raise TypeError("Not a point we support")
            """
        );
    }

    @Test
    void test12() {
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
            """
        );
    }

    @Test
    void test13() {
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
    void test14() {
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
    void test15() {
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
    void test16() {
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
    void test17() {
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
    void test18() {
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
    void test19() {
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
    void test20() {
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
    void test21() {
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
    void test22() {
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
    void test23() {
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
                case (0) { ... }
                case (y) { ... }
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
                case 0:
                    ...
                case y:
                    ...
            """
        );
    }

    @Test
    void test24() {
        String input = """
            assert (condition)
            assert (condition,)
            assert (condition, "message")
            """;
        runTest(
            input,
            FORCE_PARENS | FORCE_PARENS_IN_RETURN | OPTIONAL_SEMICOLONS,
            """
            assert (condition)
            assert (condition)
            assert (condition), ("message")
            """
        );
        runTest(
            input,
            OPTIONAL_SEMICOLONS,
            """
            assert (condition)
            assert (condition,)
            assert (condition, "message")
            """
        );
    }

    private static void runTest(String input, String expected) {
        runTest(input, 0, expected);
    }

    private static void runTest(String input, int flags, String expected) {
        var source = CharStreams.fromString(input);
        var lexer = new PyJavaLexer(source);
        lexer.addErrorListener(new ConsoleErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String msg, RecognitionException e) {
                throw e;
            }
        });
        var tokens = new CommonTokenStream(lexer);
        var parser = new PyJavaParser(tokens,
            PyJavaOptions.builder()
            .requireSemicolons((flags & REQUIRE_SEMICOLONS) != 0)
            .forceParensInStatements((flags & FORCE_PARENS) != 0)
            .forceParensInReturnYieldRaise((flags & FORCE_PARENS_IN_RETURN) != 0)
            .build()
        );
        parser.setErrorHandler(new BailErrorStrategy());
        var file = parser.file();
        var transpiler = new Transpiler();
        file.accept(transpiler);
        var sb = new StringBuilder();
        transpiler.appendTo(AppendFunction.wrap(sb));
        assertEquals(expected, sb.toString());
    }

    private static void assertException(Throwable e, Class<? extends Throwable> exceptionType) {
        assertTrue(exceptionType.isInstance(e), () -> "cause was not "+exceptionType.getName()+", was "+(e.getCause() == null? "null" : e.getCause().getClass().getName()));
    }

    private static void assertException(Throwable e, Class<? extends Throwable> exceptionType, String message) {
        assertTrue(exceptionType.isInstance(e), () -> "cause was not "+exceptionType.getName()+", was "+(e == null? "null" : e.getClass().getName()));
        assertEquals(e.getMessage(), message);
    }

    private static void assertException(Throwable e, Class<? extends Throwable> exceptionType, Supplier<? extends String> message) {
        assertTrue(exceptionType.isInstance(e), () -> "cause was not "+exceptionType.getName()+", was "+(e == null? "null" : e.getClass().getName()));
        assertEquals(e.getMessage(), message.get());
    }
}
