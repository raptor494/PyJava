package pyjava;

import static pyjava.BasicTests.runTest;

import org.junit.jupiter.api.Test;

/**
 * Test cases for lambdas and class expressions
 */
class TestCompoundExpressions {
    @Test
    void testMultilineLambda() {
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
            elems = [randrange(10) for _ in range(10)] # Generate 10 random integers from 0 to 9
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
    void testAnonymousClass() {
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
    void testAnonymousClass2() {
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
    void testMultilineAnnotatedLambda() {
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
    void testMultipleLambdas() {
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
}
