package pyjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import pyjava.parser.PyJavaLexer;
import pyjava.parser.PyJavaParser;
import pyjava.tree.LazyAppendable.AppendFunction;
import pyjava.tree.Transpiler;

class BasicTests {
    static final int REQUIRE_SEMICOLONS = 1, OPTIONAL_SEMICOLONS = 0;
    static final int FORCE_PARENS = 1 << 1;
    static final int FORCE_PARENS_IN_RETURN = 1 << 2;

    @Test
    void basicTest() {
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
    void testMissingRequiredSemicolons() {
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
    void testRequiredSemicolons() {
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

    @ParameterizedTest
    @ValueSource(strings = {
        """
        if (x < 10) {
            print(x);
        }
        """,
        """
        if (x < 10):
            print(x);
        """,
        """
        if (x < 10): print(x);
        """,
        """
        if (x < 10)
            print(x)
        """,
        """
        if (x < 10) print(x);
        """
    })
    void testBlocks(String input) {
        runTest(
            input,
            """
            if (x < 10):
                print(x)
            """
        );
    }

    @Test
    void testInconsistentIndentation() {
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
    void testIfExpression() {
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
    void testMatchExpression() {
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
    void testRidiculousSingleLineInput() {
        runTest(
            """
            def join_natural(iterable,separator=', ',word='and',oxford_comma=True,add_spaces=True){if add_spaces{if len(word)!=0 and not word[-1].isspace()word+=' ';if len(separator)!=0 and len(word)!=0 and not separator[-1].isspace()word=' '+word;}last2=None;set_last2=False;last1=None;set_last1=False;result="";for i,item in enumerate(iterable){if set_last2{if i==2 result+=str(last2);else result+=separator+str(last2);}last2=last1;set_last2=set_last1;last1=item;set_last1=True;}if set_last2{if result{if oxford_comma result+=separator+str(last2)+separator+word+str(last1);else{if add_spaces and not word[0].isspace()word=' '+word;result+=separator+str(last2)+word+str(last1);}}else{if add_spaces and not word[0].isspace()word=' '+word;result=str(last2)+word+str(last1);}}elif set_last1 result=str(last1);return result;}class LookAheadListIterator(object){def __init__(self,iterable){self.list=list(iterable);self.marker=0;self.saved_markers=[];self.default=None;self.value=None;}def __iter__(self){return self;}def set_default(self,value){self.default=value;}def next(self){return self.__next__();}def previous(self){try{self.value=self.list[self.marker-1];self.marker-=1;}except IndexError;return self.value;}def __next__(self){try{self.value=self.list[self.marker];self.marker+=1;}except IndexError raise StopIteration();return self.value;}def look(self,i=0){try{self.value=self.list[self.marker+i];}except IndexError return self.default;return self.value;}def last(self){return self.value;}def __enter__(self){self.push_marker();return self;}def __exit__(self,exc_type,exc_val,exc_tb){if exc_type or exc_val or exc_tb self.pop_marker(True);else self.pop_marker(False);}def push_marker(self){self.saved_markers.append(self.marker);}def pop_marker(self,reset){saved=self.saved_markers.pop();if reset self.marker=saved;}}
            """,
            REQUIRE_SEMICOLONS,
            """
            def join_natural(iterable, separator=', ', word='and', oxford_comma=True, add_spaces=True):
                if add_spaces:
                    if len(word) != 0 and not word[-1].isspace():
                        word += ' '
                    if len(separator) != 0 and len(word) != 0 and not separator[-1].isspace():
                        word = ' ' + word
                last2 = None
                set_last2 = False
                last1 = None
                set_last1 = False
                result = ""
                for i, item in enumerate(iterable):
                    if set_last2:
                        if i == 2:
                            result += str(last2)
                        else:
                            result += separator + str(last2)
                    last2 = last1
                    set_last2 = set_last1
                    last1 = item
                    set_last1 = True
                if set_last2:
                    if result:
                        if oxford_comma:
                            result += separator + str(last2) + separator + word + str(last1)
                        else:
                            if add_spaces and not word[0].isspace():
                                word = ' ' + word
                            result += separator + str(last2) + word + str(last1)
                    else:
                        if add_spaces and not word[0].isspace():
                            word = ' ' + word
                        result = str(last2) + word + str(last1)
                elif set_last1:
                    result = str(last1)
                return result
            class LookAheadListIterator(object):
                def __init__(self, iterable):
                    self.list = list(iterable)
                    self.marker = 0
                    self.saved_markers = []
                    self.default = None
                    self.value = None
                def __iter__(self):
                    return self
                def set_default(self, value):
                    self.default = value
                def next(self):
                    return self.__next__()
                def previous(self):
                    try:
                        self.value = self.list[self.marker - 1]
                        self.marker -= 1
                    except IndexError:
                        pass
                    return self.value
                def __next__(self):
                    try:
                        self.value = self.list[self.marker]
                        self.marker += 1
                    except IndexError:
                        raise StopIteration()
                    return self.value
                def look(self, i=0):
                    try:
                        self.value = self.list[self.marker + i]
                    except IndexError:
                        return self.default
                    return self.value
                def last(self):
                    return self.value
                def __enter__(self):
                    self.push_marker()
                    return self
                def __exit__(self, exc_type, exc_val, exc_tb):
                    if exc_type or exc_val or exc_tb:
                        self.pop_marker(True)
                    else:
                        self.pop_marker(False)
                def push_marker(self):
                    self.saved_markers.append(self.marker)
                def pop_marker(self, reset):
                    saved = self.saved_markers.pop()
                    if reset:
                        self.marker = saved
            """
        );
    }

    /*  (Blank test body for quick copy-paste)

        runTest(
            """
            
            """,

            """
            
            """
        );

    */
        
    static void runTest(String input, String expected) {
        runTest(input, 0, expected);
    }

    static void runTest(String input, int flags, String expected) {
        var source = CharStreams.fromString(input);
        var lexer = new PyJavaLexer(source);
        var errorListener = new BaseErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        var tokens = new CommonTokenStream(lexer);
        var parser = new PyJavaParser(tokens,
            PyJavaOptions.builder()
            .requireSemicolons((flags & REQUIRE_SEMICOLONS) != 0)
            .forceParensInStatements((flags & FORCE_PARENS) != 0)
            .forceParensInReturnYieldRaise((flags & FORCE_PARENS_IN_RETURN) != 0)
            .build()
        );
        parser.setErrorHandler(new BailErrorStrategy());
        //parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        var file = parser.file();
        var transpiler = new Transpiler();
        file.accept(transpiler);
        var sb = new StringBuilder();
        transpiler.appendTo(AppendFunction.wrap(sb));
        assertEquals(expected, sb.toString());
    }

    static void assertException(Throwable e, Class<? extends Throwable> exceptionType) {
        assertTrue(exceptionType.isInstance(e), () -> "cause was not "+exceptionType.getName()+", was "+(e.getCause() == null? "null" : e.getCause().getClass().getName()));
    }

    static void assertException(Throwable e, Class<? extends Throwable> exceptionType, String message) {
        assertTrue(exceptionType.isInstance(e), () -> "cause was not "+exceptionType.getName()+", was "+(e == null? "null" : e.getClass().getName()));
        assertEquals(e.getMessage(), message);
    }

    static void assertException(Throwable e, Class<? extends Throwable> exceptionType, Supplier<? extends String> message) {
        assertTrue(exceptionType.isInstance(e), () -> "cause was not "+exceptionType.getName()+", was "+(e == null? "null" : e.getClass().getName()));
        assertEquals(e.getMessage(), message.get());
    }
}
