package pyjava.parser;

import static pyjava.parser.PyJavaLexer.*;

import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import pyjava.PyJavaOptions;

public abstract class PyJavaParserBase extends Parser {
    protected PyJavaOptions options;
    protected boolean inDecorator;

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
        return options.requireSemicolons() || !here(NEWLINE);
    }

    protected boolean closeBrace() {
        return _input.LT(1).getType() == RBRACE;
    }

    protected boolean nextIsRealNumber() {
        Token tok = _input.LT(1);
        if (tok.getType() == NUMBER) {
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
        if (tok.getType() == NUMBER) {
            String text = tok.getText();
            return switch (text.charAt(text.length() - 1)) {
                case 'j', 'J' -> true;
                default -> false;
            };
        }
        return false;
    }

    public boolean isCommentToken(Token token) {
        if (token.getChannel() == Lexer.HIDDEN) {
            switch (token.getType()) {
                case BLOCK_COMMENT, LINE_COMMENT:
                    return true;
            }
        }
        return false;
    }

    /**
     * Gets the comment token at the current index of the parser's
     * token stream if there is one, otherwise returns {@code null}.
     * @return the hidden comment token or {@code null}.
     */
    protected Token getFirstPrecedingComment() {
        // Get the token ahead of the current index.
        int possibleIndexEosToken = this.getCurrentToken().getTokenIndex() - 1;
        if (possibleIndexEosToken < 0) return null;
        Token ahead = _input.get(possibleIndexEosToken);
        
        Token lastCommentToken = null;
        loop: while (ahead.getChannel() == Lexer.HIDDEN) {
            switch (ahead.getType()) {
                case BLOCK_COMMENT, LINE_COMMENT -> {
                    lastCommentToken = ahead;
                }
                case NEWLINE -> {
                    lastCommentToken = null;
                }
                default -> {
                    break loop;
                }
            }
            if (--possibleIndexEosToken < 0) break;
            ahead = _input.get(possibleIndexEosToken);
        }

        return lastCommentToken;
    }

    /**
     * Gets all comment tokens starting at the current index of the parser's
     * token stream.
     * @return a list of the comment tokens or an empty list if there were none.
     */
    protected List<Token> getPrecedingLineComments() {
        // Get the token ahead of the current index.
        int possibleIndexEosToken = this.getCurrentToken().getTokenIndex() - 1;
        if (possibleIndexEosToken < 0) return List.of();
        Token ahead = _input.get(possibleIndexEosToken);
        
        var commentTokens = new LinkedList<Token>();
        boolean addedCommentLast = false;
        loop: while (ahead.getChannel() == Lexer.HIDDEN) {
            switch (ahead.getType()) {
                case BLOCK_COMMENT, LINE_COMMENT -> {
                    commentTokens.addFirst(ahead);
                    addedCommentLast = true;
                }
                case NEWLINE -> {
                    addedCommentLast = false;
                }
                default -> {
                    break loop;
                }
            }
            if (--possibleIndexEosToken < 0) {
                addedCommentLast = false;
                break;
            }
            ahead = _input.get(possibleIndexEosToken);
        }
        if (addedCommentLast) {
            commentTokens.removeFirst();
        }

        return commentTokens;
    }

    /**
     * Returns {@code true} if on the current index of the parser's
     * token stream a token of the given {@code type} exists on the
     * {@code HIDDEN} channel.
     *
     * @param type
     *         the type of the token on the {@code HIDDEN} channel
     *         to check.
     *
     * @return {@code true} if there's a hidden token here.
     */
    private boolean here(final int type) {
        // Get the token ahead of the current index.
        int possibleIndexEosToken = this.getCurrentToken().getTokenIndex() - 1;
        if (possibleIndexEosToken < 0) return false;
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
        if (possibleIndexEosToken < 0) return false;
        Token ahead = _input.get(possibleIndexEosToken);

        if (ahead.getChannel() != Lexer.HIDDEN) {
            // We're only interested in tokens on the HIDDEN channel.
            return false;
        }

        if (ahead.getType() == NEWLINE) {
            // There is definitely a line terminator ahead.
            return true;
        }

        if (ahead.getType() == SPACES) {
            // Get the token ahead of the current whitespaces.
            possibleIndexEosToken = this.getCurrentToken().getTokenIndex() - 2;
            ahead = _input.get(possibleIndexEosToken);
        }

        // Get the token's text and type.
        String text = ahead.getText();
        int type = ahead.getType();

        // Check if the token is, or contains a line terminator.
        return type == BLOCK_COMMENT && (text.contains("\r") || text.contains("\n"))
            || type == NEWLINE;
    }
}
