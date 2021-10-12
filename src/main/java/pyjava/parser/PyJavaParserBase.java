package pyjava.parser;

import java.util.Objects;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import pyjava.PyJavaOptions;

public abstract class PyJavaParserBase extends Parser {
    protected PyJavaOptions options;

    public PyJavaParserBase(TokenStream input) {
        super(input);
        options = new PyJavaOptions();
    }

    public PyJavaParserBase(TokenStream input, PyJavaOptions optionsIn) {
        super(input);
        options = Objects.requireNonNullElseGet(optionsIn, PyJavaOptions::new);
    }

    public void setOptions(PyJavaOptions options) {
        this.options = Objects.requireNonNull(options);
    }

    protected boolean prev(String str) {
        return _input.LT(-1).getText().equals(str);
    }

    protected boolean prev(final int type) {
        return _input.LA(-1) == type;
    }

    protected boolean next(String str) {
        return _input.LT(1).getText().equals(str);
    }

    protected boolean next(final int type) {
        return _input.LA(1) == type;
    }

    protected boolean notLineTerminator() {
        return options.requireSemicolons()? true : !here(PyJavaLexer.NEWLINE);
    }

    protected boolean closeBrace() {
        return _input.LT(1).getType() == PyJavaLexer.RBRACE;
    }

    protected boolean nextIsRealNumber() {
        Token tok = _input.LT(1);
        if (tok.getType() == PyJavaLexer.NUMBER) {
            String text = tok.getText();
            return switch (text.charAt(text.length() - 1)) {
                case 'j', 'J' -> false;
                default -> true;
            };
        }
        return false;
    }

    protected boolean nextIsImagNumber() {
        Token tok = _input.LT(1);
        if (tok.getType() == PyJavaLexer.NUMBER) {
            String text = tok.getText();
            return switch (text.charAt(text.length() - 1)) {
                case 'j', 'J' -> true;
                default -> false;
            };
        }
        return false;
    }

    /**
     * Returns {@code true} iff on the current index of the parser's
     * token stream a token of the given {@code type} exists on the
     * {@code HIDDEN} channel.
     *
     * @param type
     *         the type of the token on the {@code HIDDEN} channel
     *         to check.
     *
     * @return {@code true} iff on the current index of the parser's
     * token stream a token of the given {@code type} exists on the
     * {@code HIDDEN} channel.
     */
    private boolean here(final int type) {
        // Get the token ahead of the current index.
        int possibleIndexEosToken = this.getCurrentToken().getTokenIndex() - 1;
        Token ahead = _input.get(possibleIndexEosToken);

        // Check if the token resides on the HIDDEN channel and if it's of the
        // provided type.
        return ahead.getChannel() == Lexer.HIDDEN && ahead.getType() == type;
    }

    /**
     * Returns {@code true} iff on the current index of the parser's
     * token stream a token exists on the {@code HIDDEN} channel which
     * either is a line terminator, or is a multi line comment that
     * contains a line terminator.
     *
     * @return {@code true} iff on the current index of the parser's
     * token stream a token exists on the {@code HIDDEN} channel which
     * either is a line terminator, or is a multi line comment that
     * contains a line terminator.
     */
    protected boolean lineTerminatorAhead() {
        // Get the token ahead of the current index.
        int possibleIndexEosToken = this.getCurrentToken().getTokenIndex() - 1;
        Token ahead = _input.get(possibleIndexEosToken);

        if (ahead.getChannel() != Lexer.HIDDEN) {
            // We're only interested in tokens on the HIDDEN channel.
            return false;
        }

        if (ahead.getType() == PyJavaLexer.NEWLINE) {
            // There is definitely a line terminator ahead.
            return true;
        }

        if (ahead.getType() == PyJavaLexer.SPACES) {
            // Get the token ahead of the current whitespaces.
            possibleIndexEosToken = this.getCurrentToken().getTokenIndex() - 2;
            ahead = _input.get(possibleIndexEosToken);
        }

        // Get the token's text and type.
        String text = ahead.getText();
        int type = ahead.getType();

        // Check if the token is, or contains a line terminator.
        return type == PyJavaLexer.BLOCK_COMMENT && (text.contains("\r") || text.contains("\n"))
            || type == PyJavaLexer.NEWLINE;
    }
}
