package pyjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;

import pyjava.parser.PyJavaLexer;
import pyjava.parser.PyJavaParser;
import pyjava.tree.LazyAppendable.AppendFunction;
import pyjava.tree.Transpiler;

class TestPyJava {
    private static final boolean REQUIRE_SEMICOLONS = true, OPTIONAL_SEMICOLONS = false;

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
        assertThrows(ParseCancellationException.class, () -> 
            runTest(
                """
                2 + 3 * 5
                """,
                REQUIRE_SEMICOLONS,
                """
                2 + 3 * 5
                """
            )
        );
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

    private static void runTest(String input, String expected) {
        runTest(input, false, expected);
    }

    private static void runTest(String input, boolean requireSemicolons, String expected) {
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
        var parser = new PyJavaParser(tokens);
        parser.setErrorHandler(new BailErrorStrategy());
        parser.setOptions(PyJavaOptions.builder().requireSemicolons(requireSemicolons).build());
        var file = parser.file();
        var transpiler = new Transpiler();
        file.accept(transpiler);
        var sb = new StringBuilder();
        transpiler.appendTo(AppendFunction.wrap(sb));
        assertEquals(expected, sb.toString());
    }
}
