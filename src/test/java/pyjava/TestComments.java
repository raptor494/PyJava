package pyjava;

import static pyjava.BasicTests.runTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TestComments {
    @Test
    void testBlockCommentIndentedStyle() {
        runTest(
            """
            #{
                Block comment
                style #1:
                 indented
            #}
            """,
            
            """
            # Block comment
            # style #1:
            #  indented
            """
        );
    }

    @Test
    void testBlockCommentPrecedingHashtagsStyle() {
        runTest(
            """
            #{
            # Block comment
            # style #2:
            #  preceding hashtags
            #}  
            """,

            """
            # Block comment
            # style #2:
            #  preceding hashtags
            """
        );
    }

    @Test
    void testBlockCommentPrecedingHashtagsStyleWithClosingBraceOnSameLineAsText() {
        runTest(
            """
            #{
            # Block comment
            # style #2a:
            #  preceding hashtags, close brace
            #  is on same line as last text line #}
            """,

            """
            # Block comment
            # style #2a:
            #  preceding hashtags, close brace
            #  is on same line as last text line
            """
        );
    }

    @Test
    void testBlockCommentInconsistentIndentation() {
        runTest(
            """
            #{
            # invalid block
             # comment preceding
             #hashtags,
           #result may look weird
            #}
            """,

            """
            #  # invalid block
            #   # comment preceding
            #   #hashtags,
            # #result may look weird
            """
        );
    }

    @Test
    void testBlockCommentIndentedStyleOpeningBraceOnSameLineAsText() {
        runTest(
            """
            #{ Block comment
               style #3:
                first text line is on same
                line as opening brace
            #}
            """,

            """
            # Block comment
            # style #3:
            #  first text line is on same
            #  line as opening brace
            """
        );
    }

    @Test
    void testBlockCommentFollowingStatement() {
        runTest(
            """
            x = 0 #{ Block comment
                     following statement 
                     is treated special #}
            """,

            """
            x = 0 # Block comment following statement is treated special
            """
        );
    }

    @Test
    void testMultipleComments() {
        runTest(
            """
            x = 3 #Comment after statement
            #Comment before statement
            y = 4
            """,

            """
            x = 3 # Comment after statement
            # Comment before statement
            y = 4
            """
        );
    }

    @Test
    void testMultipleComments2() {
        runTest(
            """
            # Line comment test
            #{
                Block comment Test
                Block comment Test line 2
            #} x = 3
            y = 4 # Comment after statement Test
            """,

            """
            # Line comment test
            # Block comment Test
            # Block comment Test line 2
            x = 3
            y = 4 # Comment after statement Test
            """
        );
    }

    @Test
    void testFunctionComments() {
        runTest(
            """
            # Comment before function
            def function() #{ Comment after function params #} {}
            """,

            """
            # Comment before function
            def function(): # Comment after function params
                pass
            """
        );
    }

    @Test
    void testFunctionComments2() {
        runTest(
            """
            def function() # Comment after function params
            #{
            # Comment before 
            # function body
            #}
            {
                # Function body
            }
            """,
            
            """
            def function(): # Comment after function params
                # Comment before
                # function body
                pass
                # Function body
            """
        );
    }

    @Test
    void testFunctionDecoratorComments() {
        runTest(
            """
            # Comment before decorator
            @decorator # Comment after decorator
            # Comment between decorator and function
            def function() {}
            """,

            """
            # Comment before decorator
            @decorator # Comment after decorator
            # Comment between decorator and function
            def function():
                pass
            """
        );
    }

    @Test
    void testClassDecoratorComments() {
        runTest(
            """
            # Comment before class decorator
            @decorator # Comment after decorator
            # Comment before class
            class A # Comment after class
            # Comment before class body
            {
                # Class body
            }
            """,
            
            """
            # Comment before class decorator
            @decorator # Comment after decorator
            # Comment before class
            class A: # Comment after class
                # Comment before class body
                pass
                # Class body
            """
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        """
        try # Comment after try
        {
            foo();
        }
        except Exception # Comment after except
        {
            handle();
        }
        else # Comment after else
        {
            bar();
        }
        finally # Comment after finally
        {
            finish();
        }
        """,
        """
        try { # Comment after try
            foo();
        } except Exception { # Comment after except
            handle();
        } else { # Comment after else
            bar();
        } finally { # Comment after finally
            finish();
        }
        """,
        """
        try: # Comment after try
            foo();
        except Exception: # Comment after except
            handle();
        else: # Comment after else
            bar();
        finally: # Comment after finally
            finish();
        """,
        """
        try #{ Comment after try #} {
            foo();
        } except Exception #{ Comment after except #} {
            handle();
        } else #{ Comment after else #} {
            bar();
        } finally #{ Comment after finally #} {
            finish();
        }
        """
    })
    void testCommentsInTryBlock(String input) {
        runTest(
            input,
            """
            try: # Comment after try
                foo()
            except Exception: # Comment after except
                handle()
            else: # Comment after else
                bar()
            finally: # Comment after finally
                finish()
            """
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        """
        # Comment before match
        match expr # Comment after match
        {
            # Comment before case
            case 0 # Comment after case
            {}
            case 1 {}
        }
        """,
        """
        # Comment before match
        match expr { # Comment after match
            # Comment before case
            case 0 { # Comment after case
                pass;
            }
            case 1 {}
        }
        """
    })
    void testCommentsInMatchBlock(String input) {
        runTest(
            input,
            """
            # Comment before match
            match expr: # Comment after match
                # Comment before case
                case 0: # Comment after case
                    pass
                case 1: pass
            """
        );
    }
}
