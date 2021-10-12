package pyjava.tree;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;

import pyjava.parser.PyJavaParserBaseVisitor;
import pyjava.parser.PyJavaParser.*;

public class GetGroupAtomContents extends PyJavaParserBaseVisitor<ParseTree> {
    protected ParseTree defaultVal;
    protected boolean includeTuples;

    public GetGroupAtomContents(ParseTree defaultVal, boolean includeTuples) {
        this.defaultVal = defaultVal;
        this.includeTuples = includeTuples;
    }

    @Override
    protected ParseTree defaultResult() {
        return defaultVal;
    }

    @Override
    @Deprecated
    public ParseTree visitChildren(RuleNode node) {
        return defaultVal;
    }

    @Override
    public ParseTree visitPatterns(PatternsContext ctx) {
        var pattern = ctx.pattern();
        if (pattern == null) return defaultVal;
        return pattern.accept(this);
    }

    @Override
    public ParseTree visitPattern(PatternContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public ParseTree visitOrPattern(OrPatternContext ctx) {
        if (ctx.BAR(0) != null) return defaultVal;
        return ctx.closedPattern(0).accept(this);
    }

    @Override
    public ParseTree visitGroupPattern(GroupPatternContext ctx) {
        return ctx.pattern();
    }

    @Override
    public ParseTree visitTupleSequencePattern(TupleSequencePatternContext ctx) {
        if (!includeTuples) return defaultVal;
        var openSequencePattern = ctx.openSequencePattern();
        if (openSequencePattern == null) return defaultVal;
        return openSequencePattern;
    }

    @Override
    public ParseTree visitArgument(ArgumentContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public ParseTree visitStarTargets(StarTargetsContext ctx) {
        var iter = ctx.starTarget().iterator();
        var starTarget = iter.next();
        if (iter.hasNext() || ctx.COMMA(0) != null) return defaultVal;
        return starTarget.accept(this);
    }

    @Override
    public ParseTree visitStarTarget(StarTargetContext ctx) {
        if (ctx.STAR() != null) return defaultVal;
        return ctx.targetWithStarAtom().accept(this);
    }

    @Override
    public ParseTree visitTargetStarAtom(TargetStarAtomContext ctx) {
        return ctx.starAtom().accept(this);
    }

    @Override
    public ParseTree visitStarAtomGroup(StarAtomGroupContext ctx) {
        return ctx.targetWithStarAtom();
    }

    @Override
    public ParseTree visitTupleStarAtom(TupleStarAtomContext ctx) {
        if (!includeTuples) return defaultVal;
        var starTargets = ctx.starTargets();
        if (starTargets == null) return defaultVal;
        return starTargets;
    }

    @Override
    public ParseTree visitSubjectExpr(SubjectExprContext ctx) {
        var namedExpression = ctx.namedExpression();
        if (namedExpression == null) return defaultVal;
        return namedExpression.accept(this);
    }

    @Override
    public ParseTree visitStarExpressions(StarExpressionsContext ctx) {
        var iter = ctx.starExpression().iterator();
        var starExpression = iter.next();
        if (iter.hasNext() || ctx.COMMA(0) != null) return defaultVal;
        return starExpression.accept(this);
    }

    @Override
    public ParseTree visitStarExpression(StarExpressionContext ctx) {
        var expression = ctx.expression();
        if (expression == null) return defaultVal;
        return expression.accept(this);
    }

    @Override
    public ParseTree visitStarNamedExpressions(StarNamedExpressionsContext ctx) {
        var iter = ctx.starNamedExpression().iterator();
        var starNamedExpression = iter.next();
        if (iter.hasNext() || ctx.COMMA(0) != null) return defaultVal;
        return starNamedExpression.accept(this);
    }

    @Override
    public ParseTree visitStarNamedExpression(StarNamedExpressionContext ctx) {
        var namedExpression = ctx.namedExpression();
        if (namedExpression == null) return defaultVal;
        return namedExpression.accept(this);
    }

    @Override
    public ParseTree visitAtomTPrimary(AtomTPrimaryContext ctx) {
        return ctx.atom().accept(this);
    }
    
    @Override
    public ParseTree visitNamedExpression(NamedExpressionContext ctx) {
        var expression = ctx.expression();
        if (expression == null) return defaultVal;
        return expression.accept(this);
    }

    @Override
    public ParseTree visitDisjunctionExpression(DisjunctionExpressionContext ctx) {
        return ctx.disjunction().accept(this);
    }

    @Override
    public ParseTree visitDisjunction(DisjunctionContext ctx) {
        if (ctx.conjunction(1) != null) return defaultVal;
        return ctx.conjunction(0).accept(this);
    }

    @Override
    public ParseTree visitConjunction(ConjunctionContext ctx) {
        if (ctx.inversion(1) != null) return defaultVal;
        return ctx.inversion(0).accept(this);
    }

    @Override
    public ParseTree visitInversion(InversionContext ctx) {
        var comparison = ctx.comparison();
        if (comparison == null) return defaultVal;
        return comparison.accept(this);
    }

    @Override
    public ParseTree visitComparison(ComparisonContext ctx) {
        if (ctx.compareOpBitwiseOrPair(0) != null) return defaultVal;
        return ctx.bitwiseOr().accept(this);
    }

    @Override
    public ParseTree visitBitwiseOr(BitwiseOrContext ctx) {
        if (ctx.bitwiseOr() != null) return defaultVal;
        return ctx.bitwiseXor().accept(this);
    }

    @Override
    public ParseTree visitBitwiseXor(BitwiseXorContext ctx) {
        if (ctx.bitwiseXor() != null) return defaultVal;
        return ctx.bitwiseAnd().accept(this);
    }

    @Override
    public ParseTree visitBitwiseAnd(BitwiseAndContext ctx) {
        if (ctx.bitwiseAnd() != null) return defaultVal;
        return ctx.shiftExpr().accept(this);
    }

    @Override
    public ParseTree visitShiftExpr(ShiftExprContext ctx) {
        if (ctx.shiftExpr() != null) return defaultVal;
        return ctx.sum().accept(this);
    }

    @Override
    public ParseTree visitSum(SumContext ctx) {
        if (ctx.sum() != null) return defaultVal;
        return ctx.term().accept(this);
    }

    @Override
    public ParseTree visitTerm(TermContext ctx) {
        if (ctx.term() != null) return defaultVal;
        return ctx.factor().accept(this);
    }

    @Override
    public ParseTree visitFactor(FactorContext ctx) {
        var power = ctx.power();
        if (power == null) return defaultVal;
        return power.accept(this);
    }

    @Override
    public ParseTree visitPower(PowerContext ctx) {
        if (ctx.factor() != null) return defaultVal;
        return ctx.awaitPrimary().accept(this);
    }

    @Override
    public ParseTree visitAwaitPrimary(AwaitPrimaryContext ctx) {
        if (ctx.AWAIT() != null) return defaultVal;
        return ctx.primary().accept(this);
    }

    @Override
    public ParseTree visitAtomPrimary(AtomPrimaryContext ctx) {
        return ctx.atom().accept(this);
    }

    @Override
    public ParseTree visitGroupAtom(GroupAtomContext ctx) {
        return ctx.getChild(1);
    }

    @Override
    public ParseTree visitTupleAtom(TupleAtomContext ctx) {
        if (!includeTuples) return defaultVal;
        var starNamedExpressions = ctx.starNamedExpressions();
        if (starNamedExpressions == null) return defaultVal;
        return starNamedExpressions;
    }
    
    public static ParseTree getGroupAtomContents(ParseTree input) {
        return getGroupAtomContents(input, false);
    }

    public static ParseTree getGroupAtomContents(ParseTree input, boolean includeTuples) {
        return input.accept(new GetGroupAtomContents(input, includeTuples));
    }
}
