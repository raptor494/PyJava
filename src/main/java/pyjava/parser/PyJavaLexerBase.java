package pyjava.parser;

import java.util.ArrayDeque;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

public abstract class PyJavaLexerBase extends Lexer {
    public PyJavaLexerBase() {}

    public PyJavaLexerBase(CharStream input) {
        super(input);
    }

    private Token lastToken;

    // private int templateDepth = 0;

    protected boolean isStartOfFile() {
        return lastToken == null;
    }

    // protected boolean isInTemplateString() {
    //     return templateDepth > 0;
    // }

    @Override
    public Token nextToken() {
        Token next = super.nextToken();

        if (next.getChannel() == Token.DEFAULT_CHANNEL) {
            // Keep track of the last token on the default channel.
            lastToken = next;
        }

        return next;
    }

    // protected void increaseTemplateDepth() {
    //     templateDepth++;
    // }

    // protected void decreaseTemplateDepth() {
    //     templateDepth--;
    // }

    protected static enum BracketType {
        PAREN('(', ')'), SQUARE('[', ']'), CURLY('{', '}'), ANGLE('<', '>');

        public final char openChar, closeChar;

        private BracketType(char openChar, char closeChar) {
            this.openChar = openChar;
            this.closeChar = closeChar;
        }

        public static BracketType fromOpenChar(char openChar) {
            return switch (openChar) {
                case '(' -> PAREN;
                case '[' -> SQUARE;
                case '{' -> CURLY;
                case '<' -> ANGLE;
                default -> throw new IllegalArgumentException("Unknown bracket open char: '"+openChar+"'");
            };
        }
        
        public static BracketType fromCloseChar(char closeChar) {
            return switch (closeChar) {
                case ')' -> PAREN;
                case ']' -> SQUARE;
                case '}' -> CURLY;
                case '>' -> ANGLE;
                default -> throw new IllegalArgumentException("Unknown bracket close char: '"+closeChar+"'");
            };
        }
    }

    protected final ArrayDeque<BracketType> brackets = new ArrayDeque<>();

    protected boolean inBrackets() {
        return !brackets.isEmpty();
    }

    protected boolean inBrackets(BracketType type) {
        return !brackets.isEmpty() && brackets.peek() == type;
    }

    protected boolean inParens() {
        return inBrackets(BracketType.PAREN);
    }

    protected boolean inSquareBrackets() {
        return inBrackets(BracketType.SQUARE);
    }

    protected boolean inCurlyBrackets() {
        return inBrackets(BracketType.CURLY);
    }

    protected boolean inAngleBrackets() {
        return inBrackets(BracketType.ANGLE);
    }

    protected boolean inParensOrSquareBrackets() {
        return !brackets.isEmpty() && switch (brackets.peek()) { case SQUARE, PAREN -> true; default -> false; };
    }

    protected boolean inCurlyBracketsOrNone() {
        return inCurlyBrackets() || brackets.isEmpty();
    }

    protected void enterBracket(char openChar) {
        brackets.push(BracketType.fromOpenChar(openChar));
    }

    protected void exitBracket(char closeChar) {
        var bracket = BracketType.fromCloseChar(closeChar);
        if (!brackets.isEmpty() && brackets.peek() == bracket) {
            brackets.pop();
        }
    }
}
