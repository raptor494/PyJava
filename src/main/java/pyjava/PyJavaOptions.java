package pyjava;

public record PyJavaOptions(
    boolean requireSemicolons,
    boolean allowColonSimpleBlocks,
    boolean allowNoColonSimpleBlocks,
    boolean forceParensInStatements
) {
    public static final boolean DEFAULT_REQUIRE_SEMICOLONS = false;
    public static final boolean DEFAULT_ALLOW_COLON_SIMPLE_BLOCKS = true;
    public static final boolean DEFAULT_ALLOW_NO_COLON_SIMPLE_BLOCKS = true;
    public static final boolean DEFAULT_FORCE_PARENS_IN_STATEMENTS = false;

    public PyJavaOptions() {
        this(
            DEFAULT_REQUIRE_SEMICOLONS,
            DEFAULT_ALLOW_COLON_SIMPLE_BLOCKS,
            DEFAULT_ALLOW_NO_COLON_SIMPLE_BLOCKS,
            DEFAULT_FORCE_PARENS_IN_STATEMENTS
        );
    }

    public Builder toBuilder() {
        var b = new Builder();
        b.requireSemicolons = requireSemicolons;
        b.allowColonSimpleBlocks = allowColonSimpleBlocks;
        b.allowNoColonSimpleBlocks = allowNoColonSimpleBlocks;
        b.forceParensInStatements = forceParensInStatements;
        return b;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean requireSemicolons = DEFAULT_REQUIRE_SEMICOLONS;
        private boolean allowColonSimpleBlocks = DEFAULT_ALLOW_COLON_SIMPLE_BLOCKS;
        private boolean allowNoColonSimpleBlocks = DEFAULT_ALLOW_NO_COLON_SIMPLE_BLOCKS;
        private boolean forceParensInStatements = DEFAULT_FORCE_PARENS_IN_STATEMENTS;

        public Builder requireSemicolons(boolean requireSemicolons) {
            this.requireSemicolons = requireSemicolons;
            return this;
        }

        public Builder allowColonSimpleBlocks(boolean allowColonSimpleBlocks) {
            this.allowColonSimpleBlocks = allowColonSimpleBlocks;
            return this;
        }

        public Builder allowNoColonSimpleBlocks(boolean allowNoColonSimpleBlocks) {
            this.allowNoColonSimpleBlocks = allowNoColonSimpleBlocks;
            return this;
        }

        public Builder forceParensInStatements(boolean forceParensInStatements) {
            this.forceParensInStatements = forceParensInStatements;
            return this;
        }

        public PyJavaOptions build() {
            return new PyJavaOptions(
                requireSemicolons,
                allowColonSimpleBlocks,
                allowNoColonSimpleBlocks,
                forceParensInStatements
            );
        }
    }
}
