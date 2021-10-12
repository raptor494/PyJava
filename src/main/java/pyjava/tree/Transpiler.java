package pyjava.tree;

import static pyjava.parser.PyJavaLexer.DOT;
import static pyjava.parser.PyJavaLexer.ELLIPSIS;
import static pyjava.tree.GetGroupAtomContents.getGroupAtomContents;
import static pyjava.tree.GetGroupAtom.getGroupAtom;
import static pyjava.tree.GetPrimary.getPrimary;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import pyjava.parser.PyJavaParser.*;
import pyjava.parser.PyJavaParserBaseVisitor;
import pyjava.tree.LazyAppendable.AppendFunction;
import pyjava.tree.LazyAppendable.AppenderState;

public class Transpiler extends PyJavaParserBaseVisitor<Void> {
    protected IndentationAwareAppender a;
    private LinkedList<IndentationAwareAppender> beforeStatement = new LinkedList<>();

    public Transpiler() {
        this(new IndentationAwareAppender(), 0, 0);
    }

    protected Transpiler(IndentationAwareAppender a, int syntheticLambdaCount, int syntheticClassCount) {
        this.a = Objects.requireNonNull(a);
        this.syntheticLambdaCount = syntheticLambdaCount;
        this.syntheticClassCount = syntheticClassCount;
    }

    private int syntheticLambdaCount, syntheticClassCount;

    protected String getFirstArgumentIdentifier(ArgumentsContext args) {
        if (args == null) return "object";
        var argument = args.argument(0);
        if (argument == null) return "object";
        var assignmentExpression = argument.assignmentExpression();
        var expression = assignmentExpression == null? argument.expression() : assignmentExpression;
        if (expression == null) return "object";
        var result = getPrimary(getGroupAtomContents(expression));
        if (result instanceof AtomPrimaryContext atomPrimary) {
            result = atomPrimary.atom();
        }
        if (result instanceof NamedAtomContext namedAtom) {
            return namedAtom.identifier().getText();
        }
        if (result instanceof PropertyPrimaryContext propertyPrimary) {
            if (isDottedName(propertyPrimary.primary())) {
                return propertyPrimary.identifier().getText();
            }
        }
        return "object";
    }

    protected static boolean isDottedName(PrimaryContext ctx) {
        if (ctx instanceof AtomPrimaryContext atomPrimary) {
            return atomPrimary.atom() instanceof NamedAtomContext;
        }
        if (ctx instanceof PropertyPrimaryContext propertyPrimary) {
            return isDottedName(propertyPrimary.primary());
        }
        return false;
    }

    protected String syntheticClassName(String base) {
        return "__"+base+(syntheticClassCount++);
    }

    protected String syntheticLambdaName() {
        return "__lambda"+(syntheticLambdaCount++);
    }
    
    public <T extends Throwable> void appendTo(AppendFunction<? extends T> appendFunc) throws T {
        a.doAppend(appendFunc, new AppenderState());
    }

    protected IndentationAwareAppender beforeStatement() {
        return beforeStatement.getLast();
    }

    protected void newStatement() {
        beforeStatement.addLast(a.later());
    }

    protected void endStatement() {
        beforeStatement.removeLast();
    }

    @Override
    public Void visitEmptyStatement(EmptyStatementContext ctx) {
        a.append("pass").newline();
        return null;
    }

    @Override
    public Void visitAssignmentStatement(AssignmentStatementContext ctx) {
        newStatement();
        ctx.assignment().accept(this);
        a.newline();
        endStatement();
        return null;
    }

    @Override
    public Void visitVarDeclAssignment(VarDeclAssignmentContext ctx) {
        a.append(ctx.identifier().getText());
        ctx.annotation().accept(this);
        var annotatedRhs = ctx.annotatedRhs();
        if (annotatedRhs != null) {
            a.append(" = ");
            annotatedRhs.accept(this);
        }
        return null;
    }

    @Override
    public Void visitAnnotatedAssignment(AnnotatedAssignmentContext ctx) {
        var singleTarget = ctx.singleTarget();
        if (singleTarget != null) {
            a.append('(');
            singleTarget.accept(this);
            a.append(')');
        } else {
            ctx.singleSubscriptAttributeTarget().accept(this);
        }
        ctx.annotation().accept(this);
        var annotatedRhs = ctx.annotatedRhs();
        if (annotatedRhs != null) {
            a.append(" = ");
            annotatedRhs.accept(this);
        }
        return null;
    }

    @Override
    public Void visitSingleTarget(SingleTargetContext ctx) {
        var identifier = ctx.identifier();
        if (identifier != null) {
            a.append(identifier.getText());
            return null;
        }
        var singleTarget = ctx.singleTarget();
        if (singleTarget != null) {
            a.append('(');
            singleTarget.accept(this);
            a.append(')');
            return null;
        }
        else {
            ctx.singleSubscriptAttributeTarget().accept(this);
            return null;
        }
    }

    @Override
    public Void visitSingleSubscriptAttributeTarget(SingleSubscriptAttributeTargetContext ctx) {
        ctx.tPrimary().accept(this);
        var identifier = ctx.identifier();
        if (identifier != null) {
            a.append('.').append(identifier.getText());
        } else {
            a.append('[');
            ctx.slices().accept(this);
            a.append(']');
        }
        return null;
    }

    @Override
    public Void visitMultipleAssignment(MultipleAssignmentContext ctx) {
        for (var starTargets : ctx.starTargets()) {
            starTargets.accept(this);
            a.append(" = ");
        }
        ctx.annotatedRhs().accept(this);
        return null;
    }

    @Override
    public Void visitStarExpressions(StarExpressionsContext ctx) {
        var iter = ctx.starExpression().iterator();
        iter.next().accept(this);
        if (iter.hasNext()) {
            do {
                a.append(", ");
                iter.next().accept(this);
            } while (iter.hasNext());
        } else if (ctx.COMMA(0) != null) {
            a.append(',');
        }
        return null;
    }

    @Override
    public Void visitStarExpression(StarExpressionContext ctx) {
        var expression = ctx.expression();
        if (expression != null) {
            expression.accept(this);
        } else {
            a.append('*');
            ctx.bitwiseOr().accept(this);
        }
        return null;
    }

    @Override
    public Void visitStarNamedExpressions(StarNamedExpressionsContext ctx) {
        var iter = ctx.starNamedExpression().iterator();
        iter.next().accept(this);
        if (iter.hasNext()) {
            do {
                a.append(", ");
                iter.next().accept(this);
            } while (iter.hasNext());
        } else if (ctx.COMMA(0) != null) {
            a.append(',');
        }
        return null;
    }

    @Override
    public Void visitStarNamedExpression(StarNamedExpressionContext ctx) {
        var namedExpression = ctx.namedExpression();
        if (namedExpression != null) {
            namedExpression.accept(this);
        } else {
            a.append('*');
            ctx.bitwiseOr().accept(this);
        }
        return null;
    }

    @Override
    public Void visitAugAssignment(AugAssignmentContext ctx) {
        ctx.singleTarget().accept(this);
        a.append(' ').append(ctx.augAssign().getText()).append(' ');
        ctx.annotatedRhs().accept(this);
        return null;
    }

    @Override
    public Void visitStarTargets(StarTargetsContext ctx) {
        var iter = ctx.starTarget().iterator();
        iter.next().accept(this);
        if (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
            while (iter.hasNext()) {
                a.append(", ");
                iter.next().accept(this);
            }
        } else if (ctx.COMMA(0) != null) {
            a.append(',');
        }
        return null;
    }

    @Override
    public Void visitStarTarget(StarTargetContext ctx) {
        if (ctx.STAR() != null) {
            a.append('*');
        }
        ctx.targetWithStarAtom().accept(this);
        return null;
    }

    @Override
    public Void visitPropertyTargetWithStarAtom(PropertyTargetWithStarAtomContext ctx) {
        ctx.tPrimary().accept(this);
        a.append('.').append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitSliceTargetWithStarAtom(SliceTargetWithStarAtomContext ctx) {
        ctx.tPrimary().accept(this);
        a.append('[');
        ctx.slices().accept(this);
        a.append(']');
        return null;
    }

    @Override
    public Void visitTargetStarAtom(TargetStarAtomContext ctx) {
        return ctx.starAtom().accept(this);
    }

    @Override
    public Void visitNamedStarAtom(NamedStarAtomContext ctx) {
        a.append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitStarAtomGroup(StarAtomGroupContext ctx) {
        a.append('(');
        ctx.targetWithStarAtom().accept(this);
        a.append(')');
        return null;
    }

    @Override
    public Void visitTupleStarAtom(TupleStarAtomContext ctx) {
        a.append('(');
        var starTargets = ctx.starTargets();
        if (starTargets != null) {
            starTargets.accept(this);
        }
        a.append(')');
        return null;
    }

    @Override
    public Void visitListStarAtom(ListStarAtomContext ctx) {
        a.append('[');
        var starTargets = ctx.starTargets();
        if (starTargets != null) {
            starTargets.accept(this);
        }
        a.append(']');
        return null;
    }

    @Override
    public Void visitAnnotation(AnnotationContext ctx) {
        a.append(": ");
        ctx.expression().accept(this);
        return null;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatementContext ctx) {
        newStatement();
        ctx.starExpressions().accept(this);
        a.newline();
        endStatement();
        return null;
    }

    @Override
    public Void visitReturnStatement(ReturnStatementContext ctx) {
        newStatement();
        a.append("return");
        var starExpressions = ctx.starExpressions();
        if (starExpressions != null) {
            a.append(' ');
            starExpressions.accept(this);
        }
        a.newline();
        endStatement();
        return null;
    }

    @Override
    public Void visitYieldStatement(YieldStatementContext ctx) {
        newStatement();
        ctx.yieldExpression().accept(this);
        a.newline();
        endStatement();
        return null;
    }

    @Override
    public Void visitYieldExpression(YieldExpressionContext ctx) {
        var expression = ctx.expression();
        var starExpressions = ctx.starExpressions();
        if (expression != null) {
            a.append("yield from ");
            expression.accept(this);
        } else if (starExpressions != null) {
            a.append("yield ");
            starExpressions.accept(this);
        } else {
            a.append("yield");
        }
        return null;
    }

    @Override
    public Void visitRaiseFromStatement(RaiseFromStatementContext ctx) {
        newStatement();
        a.append("raise ");
        ctx.expression(0).accept(this);
        a.append(" from ");
        ctx.expression(1).accept(this);
        a.newline();
        endStatement();
        return null;
    }

    @Override
    public Void visitRaiseStatement(RaiseStatementContext ctx) {
        newStatement();
        a.append("raise");
        var expression = ctx.expression();
        if (expression != null) {
            a.append(' ');
            expression.accept(this);
        }
        a.newline();
        endStatement();
        return null;
    }

    @Override
    public Void visitImportStatement(ImportStatementContext ctx) {
        a.append("import ");
        ctx.dottedAsNames().accept(this);
        a.newline();
        return null;
    }

    @Override
    public Void visitDottedAsNames(DottedAsNamesContext ctx) {
        var iter = ctx.dottedAsName().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitDottedAsName(DottedAsNameContext ctx) {
        ctx.name.accept(this);
        if (ctx.alias != null) {
            a.append(" as ").append(ctx.alias.getText());
        }
        return null;
    }

    @Override
    public Void visitDottedName(DottedNameContext ctx) {
        a.append(ctx.identifier().stream().map(IdentifierContext::getText).collect(Collectors.joining(".")));
        return null;
    }

    @Override
    public Void visitFromImportStatement(FromImportStatementContext ctx) {
        a.append("from ");
        ctx.importFromName().accept(this);
        a.append(" import ");
        ctx.importFromTargets().accept(this);
        a.newline();
        return null;
    }

    @Override
    public Void visitImportFromName(ImportFromNameContext ctx) {
        var dots = ctx.dots();
        if (dots != null) {
            dots.accept(this);
        }
        var dottedName = ctx.dottedName();
        if (dottedName != null) {
            dottedName.accept(this);
        }
        return null;
    }

    @Override
    public Void visitImportFromTargets(ImportFromTargetsContext ctx) {
        var importFromAsNames = ctx.importFromAsNames();
        if (importFromAsNames != null) {
            importFromAsNames.accept(this);
        } else {
            a.append('*');
        }
        return null;
    }

    @Override
    public Void visitImportFromAsNames(ImportFromAsNamesContext ctx) {
        var iter = ctx.importFromAsName().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitImportFromAsName(ImportFromAsNameContext ctx) {
        a.append(ctx.name.getText());
        if (ctx.alias != null) {
            a.append(" as ").append(ctx.alias.getText());
        }
        return null;
    }

    @Override
    public Void visitDots(DotsContext ctx) {
        int dotsCount = 0;
        for (var child : ctx.children) {
            if (child instanceof TerminalNode node) {
                switch (node.getSymbol().getType()) {
                    case DOT -> dotsCount++;
                    case ELLIPSIS -> dotsCount += 3;
                }
            }
        }
        var chars = new char[dotsCount];
        Arrays.fill(chars, '.');
        a.append(new String(chars));
        return null;
    }

    @Override
    public Void visitDelStatement(DelStatementContext ctx) {
        newStatement();
        a.append("del ");
        ctx.delTargets().accept(this);
        a.newline();
        endStatement();
        return null;
    }

    @Override
    public Void visitDelTargets(DelTargetsContext ctx) {
        var iter = ctx.delTarget().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitPropertyDelTarget(PropertyDelTargetContext ctx) {
        ctx.tPrimary().accept(this);
        a.append('.').append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitSliceDelTarget(SliceDelTargetContext ctx) {
        ctx.tPrimary().accept(this);
        a.append('[');
        ctx.slices().accept(this);
        a.append(']');
        return null;
    }

    @Override
    public Void visitNameDelTarget(NameDelTargetContext ctx) {
        a.append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitDelTargetParens(DelTargetParensContext ctx) {
        return ctx.delTarget().accept(this);
    }

    @Override
    public Void visitDelTargetList(DelTargetListContext ctx) {
        if (ctx.LBRACK() == null) {
            a.append('(');
            var iter = ctx.delTarget().iterator();
            if (iter.hasNext()) {
                iter.next().accept(this);
                if (iter.hasNext()) {
                    do {
                        a.append(", ");
                        iter.next().accept(this);
                    } while (iter.hasNext());
                } else {
                    a.append(',');
                }
            }
            a.append(')');
        } else {
            a.append('[');
            var iter = ctx.delTarget().iterator();
            if (iter.hasNext()) {
                iter.next().accept(this);
                while (iter.hasNext()) {
                    a.append(", ");
                    iter.next().accept(this);
                }
            }
            a.append(']');
        }
        return null;
    }

    @Override
    public Void visitAssertStatement(AssertStatementContext ctx) {
        newStatement();
        a.append("assert ");
        var iter = ctx.expression().iterator();
        iter.next().accept(this);
        if (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        a.newline();
        endStatement();
        return null;
    }

    @Override
    public Void visitBreakStatement(BreakStatementContext ctx) {
        a.append("break").newline();
        return null;
    }

    @Override
    public Void visitContinueStatement(ContinueStatementContext ctx) {
        a.append("continue").newline();
        return null;
    }

    @Override
    public Void visitGlobalStatement(GlobalStatementContext ctx) {
        a.append("global ");
        var iter = ctx.identifier().iterator();
        a.append(iter.next().getText());
        while (iter.hasNext()) {
            a.append(", ").append(iter.next().getText());
        }
        a.newline();
        return null;
    }

    @Override
    public Void visitNonLocalStatement(NonLocalStatementContext ctx) {
        a.append("nonlocal ");
        var iter = ctx.identifier().iterator();
        a.append(iter.next().getText());
        while (iter.hasNext()) {
            a.append(", ").append(iter.next().getText());
        }
        a.newline();
        return null;
    }

    @Override
    public Void visitFunctionDef(FunctionDefContext ctx) {
        newStatement();
        var decorators = ctx.decorators();
        if (decorators != null) {
            decorators.accept(this);
        }
        ctx.funcHeader().accept(this);
        var retType = ctx.retType();
        if (retType != null) {
            retType.accept(this);
        }
        var funcBody = ctx.funcBody();
        var statements = funcBody.statement();
        var iter = statements.iterator();
        
        var strLiteral = ctx.STRING_LITERAL();
        if (strLiteral != null) {
            a.append(':').incrIndent().newline().append(strLiteral.getText()).newline();
        } else {
            if (statements.size() == 1) {
                var first = statements.get(0);
                if (first instanceof ExpressionStatementContext exprStmt) {
                    var expr = getGroupAtom(exprStmt.starExpressions());
                    if (expr instanceof EllipsisAtomContext) {
                        a.append(": ...").newline();
                        return null;
                    }
                }
            }
            a.append(':').incrIndent().newline();
        }
        if (iter.hasNext()) {
            do {
                iter.next().accept(this);
            } while (iter.hasNext());
            a.decrIndentNewline();
        } else {
            if (strLiteral == null) {
                a.append("pass");
            }
            a.decrIndent().newline();
        }
        endStatement();
        return null;
    }

    @Override
    public Void visitFuncHeader(FuncHeaderContext ctx) {
        if (ctx.ASYNC() != null) {
            a.append("async def ");
        } else {
            a.append("def ");
        }
        a.append(ctx.identifier().getText()).append('(');
        var parameters = ctx.parameters();
        if (parameters != null) {
            parameters.accept(this);
        }
        a.append(')');
        return null;
    }

    @Override
    public Void visitParameters(ParametersContext ctx) {
        var slashNoDefault = ctx.slashNoDefault();
        var slashWithDefault = ctx.slashWithDefault();
        var paramsNoDefault = ctx.paramsNoDefault();
        var paramsWithDefault = ctx.paramsWithDefault();
        var starEtc = ctx.starEtc();
        if (slashNoDefault != null) {
            slashNoDefault.accept(this);
            if (paramsNoDefault != null) {
                a.append(", ");
                paramsNoDefault.accept(this);
            }
            if (paramsWithDefault != null) {
                a.append(", ");
                paramsWithDefault.accept(this);
            }
            if (starEtc != null) {
                a.append(", ");
                starEtc.accept(this);
            }
        } else if (slashWithDefault != null) {
            slashWithDefault.accept(this);
            if (paramsWithDefault != null) {
                a.append(", ");
                paramsWithDefault.accept(this);
            }
            if (starEtc != null) {
                a.append(", ");
                starEtc.accept(this);
            }
        } else if (paramsNoDefault != null) {
            paramsNoDefault.accept(this);
            if (paramsWithDefault != null) {
                a.append(", ");
                paramsWithDefault.accept(this);
            }
            if (starEtc != null) {
                a.append(", ");
                starEtc.accept(this);
            }
        } else if (paramsWithDefault != null) {
            paramsWithDefault.accept(this);
            if (starEtc != null) {
                a.append(", ");
                starEtc.accept(this);
            }
        } else {
            assert starEtc != null;
            starEtc.accept(this);
        }
        return null;
    }

    @Override
    public Void visitSlashNoDefault(SlashNoDefaultContext ctx) {
        ctx.paramsNoDefault().accept(this);
        a.append(", /");
        return null;
    }

    @Override
    public Void visitSlashWithDefault(SlashWithDefaultContext ctx) {
        var iter1 = ctx.paramNoDefault().iterator();
        var iter2 = ctx.paramWithDefault().iterator();
        if (iter1.hasNext()) {
            iter1.next().accept(this);
            while (iter1.hasNext()) {
                a.append(", ");
                iter1.next().accept(this);
            }
        } else {
            iter2.next().accept(this);
        }
        while (iter2.hasNext()) {
            a.append(", ");
            iter2.next().accept(this);
        }
        a.append(", /");
        return null;
    }

    @Override
    public Void visitParamsNoDefault(ParamsNoDefaultContext ctx) {
        var iter = ctx.paramNoDefault().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitParamNoDefault(ParamNoDefaultContext ctx) {
        a.append(ctx.identifier().getText());
        var annotation = ctx.annotation();
        if (annotation != null) {
            annotation.accept(this);
        }
        return null;
    }

    @Override
    public Void visitParamsWithDefault(ParamsWithDefaultContext ctx) {
        var iter = ctx.paramWithDefault().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitParamWithDefault(ParamWithDefaultContext ctx) {
        a.append(ctx.identifier().getText());
        var annotation = ctx.annotation();
        if (annotation != null) {
            annotation.accept(this);
        }
        ctx.defaultVal().accept(this);
        return null;
    }

    @Override
    public Void visitDefaultVal(DefaultValContext ctx) {
        a.append('=');
        ctx.expression().accept(this);
        return null;
    }

    @Override
    public Void visitParamMaybeDefault(ParamMaybeDefaultContext ctx) {
        a.append(ctx.identifier().getText());
        var annotation = ctx.annotation();
        if (annotation != null) {
            annotation.accept(this);
        }
        var defaultVal = ctx.defaultVal();
        if (defaultVal != null) {
            defaultVal.accept(this);
        }
        return null;
    }

    @Override
    public Void visitStarEtc(StarEtcContext ctx) {
        var paramNoDefault = ctx.paramNoDefault();
        var iter = ctx.paramMaybeDefault().iterator();
        var kwds = ctx.kwds();
        if (paramNoDefault != null) {
            a.append('*');
            paramNoDefault.accept(this);
            while (iter.hasNext()) {
                a.append(", ");
                iter.next().accept(this);
            }
            if (kwds != null) {
                a.append(", ");
                kwds.accept(this);
            }
        } else if (iter.hasNext()) {
            a.append('*');
            do {
                a.append(", ");
                iter.next().accept(this);
            } while (iter.hasNext());
            if (kwds != null) {
                a.append(", ");
                kwds.accept(this);
            }
        } else {
            assert kwds != null;
            kwds.accept(this);
        }
        return null;
    }

    @Override
    public Void visitKwds(KwdsContext ctx) {
        a.append("**");
        ctx.paramNoDefault().accept(this);
        return null;
    }

    @Override
    public Void visitRetType(RetTypeContext ctx) {
        a.append(" -> ");
        ctx.expression().accept(this);
        return null;
    }

    @Override
    public Void visitClassDef(ClassDefContext ctx) {
        newStatement();
        var decorators = ctx.decorators();
        if (decorators != null) {
            decorators.accept(this);
        }
        ctx.classHeader().accept(this);
        a.append(':').incrIndent().newline();
        var strLiteral = ctx.STRING_LITERAL();
        if (strLiteral != null) {
            a.append(strLiteral.getText());
        }
        var iter = ctx.classBody().statement().iterator();
        if (iter.hasNext()) {
            do {
                iter.next().accept(this);
            } while (iter.hasNext());
            a.decrIndentNewline();
        } else {
            a.append("pass").decrIndent().newline();
        }
        endStatement();
        return null;
    }

    @Override
    public Void visitClassHeader(ClassHeaderContext ctx) {
        a.append("class ").append(ctx.identifier().getText());
        if (ctx.LPAREN() != null) {
            a.append('(');
            var arguments = ctx.arguments();
            if (arguments != null) {
                arguments.accept(this);
            }
            a.append(')');
        }
        return null;
    }

    @Override
    public Void visitDecorators(DecoratorsContext ctx) {
        for (var decorator : ctx.decorator()) {
            decorator.accept(this);
        }
        return null;
    }

    @Override
    public Void visitDecorator(DecoratorContext ctx) {
        a.append('@');
        ctx.namedExpression().accept(this);
        a.newline();
        return null;
    }

    @Override
    public Void visitArguments(ArgumentsContext ctx) {
        var iter = ctx.argument().iterator();
        var kwargs = ctx.kwargs();
        if (iter.hasNext()) {
            iter.next().accept(this);
            while (iter.hasNext()) {
                a.append(", ");
                iter.next().accept(this);
            }
            if (kwargs != null) {
                a.append(", ");
                kwargs.accept(this);
            }
        } else {
            assert kwargs != null;
            kwargs.accept(this);
        }
        return null;
    }

    @Override
    public Void visitArgument(ArgumentContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public Void visitStarredExpression(StarredExpressionContext ctx) {
        a.append('*');
        ctx.expression().accept(this);
        return null;
    }

    @Override
    public Void visitAssignmentExpression(AssignmentExpressionContext ctx) {
        a.append(ctx.identifier().getText()).append(" := ");
        ctx.expression().accept(this);
        return null;
    }

    @Override
    public Void visitKwargs(KwargsContext ctx) {
        var iter1 = ctx.kwargOrStarred().iterator();
        var iter2 = ctx.kwargOrDoubleStarred().iterator();
        if (iter1.hasNext()) {
            iter1.next().accept(this);
            while (iter1.hasNext()) {
                a.append(", ");
                iter1.next().accept(this);
            }
        } else {
            iter2.next().accept(this);
        }
        while (iter2.hasNext()) {
            a.append(", ");
            iter2.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitKwargOrStarred(KwargOrStarredContext ctx) {
        var starredExpression = ctx.starredExpression();
        if (starredExpression != null) {
            starredExpression.accept(this);
        } else {
            a.append(ctx.identifier().getText()).append('=');
            ctx.expression().accept(this);
        }
        return null;
    }

    @Override
    public Void visitKwargOrDoubleStarred(KwargOrDoubleStarredContext ctx) {
        var identifier = ctx.identifier();
        if (identifier != null) {
            a.append(ctx.identifier().getText()).append('=');
        } else {
            a.append("**");
        }
        ctx.expression().accept(this);
        return null;
    }

    @Override
    public Void visitNamedExpressionCond(NamedExpressionCondContext ctx) {
        if (ctx.LPAREN() != null) {
            if (ctx.NOT() != null) {
                a.append("not (");
            } else {
                a.append('(');
            }
            ctx.namedExpression().accept(this);
            a.append(')');
        } else {
            ctx.namedExpression().accept(this);
        }
        return null;
    }

    @Override
    public Void visitNamedExpression(NamedExpressionContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public Void visitAnnotatedRhs(AnnotatedRhsContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    protected ParseTree getCond(ParseTree input) {
        // return getGroupAtomContents(input);
        return input;
    }

    protected ParseTree getTupleCond(ParseTree input) {
        // return getGroupAtomContents(input, true);
        return input;
    }

    @Override
    public Void visitIfStatement(IfStatementContext ctx) {
        newStatement();
        a.append("if ");
        getCond(ctx.namedExpressionCond()).accept(this);
        ctx.block().accept(this);
        for (var elif : ctx.elif()) {
            elif.accept(this);
        }
        var elseBlock = ctx.elseBlock();
        if (elseBlock != null) {
            elseBlock.accept(this);
        }
        endStatement();
        return null;
    }

    @Override
    public Void visitElif(ElifContext ctx) {
        a.append("elif ");
        getCond(ctx.namedExpressionCond()).accept(this);
        ctx.block().accept(this);
        return null;
    }

    @Override
    public Void visitElseBlock(ElseBlockContext ctx) {
        a.append("else");
        ctx.block().accept(this);
        return null;
    }

    @Override
    public Void visitFuncBody(FuncBodyContext ctx) {
        var statements = ctx.statement();
        var iter = statements.iterator();
        if (statements.size() == 1) {
            var first = statements.get(0);
            if (first instanceof ExpressionStatementContext exprStmt) {
                var expr = getGroupAtom(exprStmt.starExpressions());
                if (expr instanceof EllipsisAtomContext) {
                    a.append(": ...").newline();
                    return null;
                }
            }
        }
        a.append(':');
        if (iter.hasNext()) {    
            a.incrIndent().newline();
            do {
                iter.next().accept(this);
            } while (iter.hasNext());
            a.decrIndentNewline();
        } else {
            a.append(" pass").newline();
        }
        return null;
    }

    @Override
    public Void visitClassBody(ClassBodyContext ctx) {
        var iter = ctx.statement().iterator();
        a.append(':');
        if (iter.hasNext()) {
            a.incrIndent().newline();
            do {
                iter.next().accept(this);
            } while (iter.hasNext());
            a.decrIndentNewline();
        } else {
            a.append(" pass").newline();
        }
        return null;
    }

    @Override
    public Void visitBlock(BlockContext ctx) {
        var iter = ctx.statement().iterator();
        a.append(':');
        if (iter.hasNext()) {
            a.incrIndent().newline();
            do {
                iter.next().accept(this);
            } while (iter.hasNext());
            a.decrIndentNewline();
        } else {
            a.append(" pass").newline();
        }
        return null;
    }

    @Override
    public Void visitWhileLoop(WhileLoopContext ctx) {
        newStatement();
        a.append("while ");
        getCond(ctx.namedExpressionCond()).accept(this);
        ctx.block().accept(this);
        var elseBlock = ctx.elseBlock();
        if (elseBlock != null) {
            elseBlock.accept(this);
        }
        endStatement();
        return null;
    }

    @Override
    public Void visitForLoop(ForLoopContext ctx) {
        newStatement();
        if (ctx.ASYNC() != null) {
            a.append("async for ");
        } else {
            a.append("for ");
        }
        ctx.forLoopHeader().accept(this);
        ctx.block().accept(this);
        var elseBlock = ctx.elseBlock();
        if (elseBlock != null) {
            elseBlock.accept(this);
        }
        endStatement();
        return null;
    }

    @Override
    public Void visitForLoopHeader(ForLoopHeaderContext ctx) {
        getTupleCond(ctx.starTargets()).accept(this);
        a.append(" in ");
        getTupleCond(ctx.starExpressions()).accept(this);
        return null;
    }

    @Override
    public Void visitWithStatement(WithStatementContext ctx) {
        newStatement();
        if (ctx.ASYNC() != null) {
            a.append("async with ");
        } else {
            a.append("with ");
        }
        ctx.withItems().accept(this);
        ctx.block().accept(this);
        endStatement();
        return null;
    }

    @Override
    public Void visitWithItems(WithItemsContext ctx) {
        var iter = ctx.withItem().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitWithItem(WithItemContext ctx) {
        ctx.expression().accept(this);
        var starTarget = ctx.starTarget();
        if (starTarget != null) {
            a.append(" as ");
            starTarget.accept(this);
        }
        return null;
    }

    @Override
    public Void visitTryFinallyStatement(TryFinallyStatementContext ctx) {
        a.append("try");
        ctx.block().accept(this);
        ctx.finallyBlock().accept(this);
        return null;
    }

    @Override
    public Void visitTryExceptStatement(TryExceptStatementContext ctx) {
        newStatement();
        a.append("try");
        ctx.block().accept(this);
        for (var exceptBlock : ctx.exceptBlock()) {
            exceptBlock.accept(this);
        }
        var elseBlock = ctx.elseBlock();
        if (elseBlock != null) {
            elseBlock.accept(this);
        }
        var finallyBlock = ctx.finallyBlock();
        if (finallyBlock != null) {
            finallyBlock.accept(this);
        }
        endStatement();
        return null;
    }

    @Override
    public Void visitExceptBlock(ExceptBlockContext ctx) {
        a.append("except");
        var exceptItem = ctx.exceptItem();
        if (exceptItem != null) {
            a.append(' ');
            exceptItem.accept(this);
        }
        ctx.block().accept(this);
        return null;
    }

    @Override
    public Void visitExceptItem(ExceptItemContext ctx) {
        ctx.expression().accept(this);
        var identifier = ctx.identifier();
        if (identifier != null) {
            a.append(" as ").append(identifier.getText());
        }
        return null;
    }

    @Override
    public Void visitFinallyBlock(FinallyBlockContext ctx) {
        a.append("finally");
        ctx.block().accept(this);
        return null;
    }

    @Override
    public Void visitMatchStatement(MatchStatementContext ctx) {
        newStatement();
        a.append("match ");
        ctx.subjectExpr().accept(this);
        a.append(':').incrIndent().newline();
        for (var caseBlock : ctx.caseBlock()) {
            caseBlock.accept(this);
        }
        a.decrIndentNewline();
        endStatement();
        return null;
    }

    @Override
    public Void visitSubjectExpr(SubjectExprContext ctx) {
        var namedExpression = ctx.namedExpression();
        if (namedExpression != null) {
            namedExpression.accept(this);
        } else {
            ctx.starNamedExpression().accept(this);
            var starNamedExpressions = ctx.starNamedExpressions();
            if (starNamedExpressions != null) {
                a.append(", ");
                starNamedExpressions.accept(this);
            } else {
                a.append(',');
            }
        }
        return null;
    }

    @Override
    public Void visitCaseBlock(CaseBlockContext ctx) {
        a.append("case ");
        ctx.patterns().accept(this);
        var guard = ctx.guard();
        if (guard != null) {
            a.append(' ');
            guard.accept(this);
        }
        ctx.block().accept(this);
        return null;
    }

    @Override
    public Void visitGuard(GuardContext ctx) {
        a.append("if ");
        ctx.namedExpression().accept(this);
        return null;
    }

    @Override
    public Void visitPatterns(PatternsContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public Void visitPattern(PatternContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public Void visitAsPattern(AsPatternContext ctx) {
        ctx.orPattern().accept(this);
        a.append(" as ");
        ctx.patternCaptureTarget().accept(this);
        return null;
    }

    @Override
    public Void visitOrPattern(OrPatternContext ctx) {
        var iter = ctx.closedPattern().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(" | ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitLiteralPattern(LiteralPatternContext ctx) {
        return ctx.literalExpr().accept(this);
    }

    @Override
    public Void visitCapturePattern(CapturePatternContext ctx) {
        return ctx.patternCaptureTarget().accept(this);
    }

    @Override
    public Void visitWildcardPattern(WildcardPatternContext ctx) {
        a.append('_');
        return null;
    }

    @Override
    public Void visitValuePattern(ValuePatternContext ctx) {
        return ctx.attr().accept(this);
    }

    @Override
    public Void visitGroupPattern(GroupPatternContext ctx) {
        a.append('(');
        ctx.pattern().accept(this);
        a.append(')');
        return null;
    }

    @Override
    public Void visitListSequencePattern(ListSequencePatternContext ctx) {
        a.append('[');
        var maybeSequencePattern = ctx.maybeSequencePattern();
        if (maybeSequencePattern != null) {
            maybeSequencePattern.accept(this);
        }
        a.append(']');
        return null;
    }

    @Override
    public Void visitTupleSequencePattern(TupleSequencePatternContext ctx) {
        a.append('(');
        var openSequencePattern = ctx.openSequencePattern();
        if (openSequencePattern != null) {
            openSequencePattern.accept(this);
        }
        a.append(')');
        return null;
    }

    @Override
    public Void visitMappingPattern(MappingPatternContext ctx) {
        var itemsPattern = ctx.itemsPattern();
        var doubleStarPattern = ctx.doubleStarPattern();
        if (itemsPattern != null) {
            a.append("{ ");
            itemsPattern.accept(this);
            if (doubleStarPattern != null) {
                a.append(", ");
                doubleStarPattern.accept(this);
            }
            a.append(" }");
        } else if (doubleStarPattern != null) {
            a.append("{ ");
            doubleStarPattern.accept(this);
            a.append(" }");
        } else {
            a.append("{}");
        }
        return null;
    }

    @Override
    public Void visitItemsPattern(ItemsPatternContext ctx) {
        var iter = ctx.keyValuePattern().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitKeyValuePattern(KeyValuePatternContext ctx) {
        ctx.getChild(0).accept(this);
        a.append(": ");
        ctx.pattern().accept(this);
        return null;
    }

    @Override
    public Void visitDoubleStarPattern(DoubleStarPatternContext ctx) {
        a.append("**");
        ctx.patternCaptureTarget().accept(this);
        return null;
    }

    @Override
    public Void visitComplexLiteralPattern(ComplexLiteralPatternContext ctx) {
        return ctx.complexNumber().accept(this);
    }

    @Override
    public Void visitNumberLiteralPattern(NumberLiteralPatternContext ctx) {
        return ctx.signedNumber().accept(this);
    }

    @Override
    public Void visitStringLiteralPattern(StringLiteralPatternContext ctx) {
        return ctx.strings().accept(this);
    }

    @Override
    public Void visitStrings(StringsContext ctx) {
        var iter = ctx.children.iterator();
        a.append(iter.next().getText());
        while (iter.hasNext()) {
            a.append(' ').append(iter.next().getText());
        }
        return null;
    }

    @Override
    public Void visitNoneLiteralPattern(NoneLiteralPatternContext ctx) {
        a.append("None");
        return null;
    }

    @Override
    public Void visitTrueLiteralPattern(TrueLiteralPatternContext ctx) {
        a.append("True");
        return null;
    }

    @Override
    public Void visitFalseLiteralPattern(FalseLiteralPatternContext ctx) {
        a.append("False");
        return null;
    }

    @Override
    public Void visitPatternCaptureTarget(PatternCaptureTargetContext ctx) {
        a.append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitClassPattern(ClassPatternContext ctx) {
        ctx.nameOrAttr().accept(this);
        var positionalPatterns = ctx.positionalPatterns();
        var keywordPatterns = ctx.keywordPatterns();
        a.append('(');
        if (positionalPatterns != null) {
            positionalPatterns.accept(this);
            if (keywordPatterns != null) {
                a.append(", ");
                keywordPatterns.accept(this);
            }
        } else if (keywordPatterns != null) {
            keywordPatterns.accept(this);
        }
        a.append(')');
        return null;
    }

    @Override
    public Void visitPositionalPatterns(PositionalPatternsContext ctx) {
        var iter = ctx.pattern().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitKeywordPatterns(KeywordPatternsContext ctx) {
        var iter = ctx.keywordPattern().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitKeywordPattern(KeywordPatternContext ctx) {
        a.append(ctx.identifier().getText()).append('=');
        ctx.pattern().accept(this);
        return null;
    }

    @Override
    public Void visitMaybeSequencePattern(MaybeSequencePatternContext ctx) {
        var iter = ctx.maybeStarPattern().iterator();
        iter.next().accept(this);
        if (iter.hasNext()) {
            do {
                a.append(", ");
                iter.next().accept(this);
            } while (iter.hasNext());
        } else if (ctx.COMMA(0) != null) {
            a.append(',');
        }
        return null;
    }

    @Override
    public Void visitMaybeStarPattern(MaybeStarPatternContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public Void visitOpenSequencePattern(OpenSequencePatternContext ctx) {
        ctx.maybeStarPattern().accept(this);
        var maybeSequencePattern = ctx.maybeSequencePattern();
        if (maybeSequencePattern != null) {
            a.append(", ");
            maybeSequencePattern.accept(this);
        } else {
            a.append(',');
        }
        return null;
    }

    @Override
    public Void visitStarPattern(StarPatternContext ctx) {
        a.append('*');
        var patternCaptureTarget = ctx.patternCaptureTarget();
        if (patternCaptureTarget != null) {
            patternCaptureTarget.accept(this);
        } else {
            a.append('_');
        }
        return null;
    }

    @Override
    public Void visitNameOrAttr(NameOrAttrContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public Void visitAttr(AttrContext ctx) {
        ctx.dottedName().accept(this);
        a.append('.').append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitIfExpression(IfExpressionContext ctx) {
        ctx.disjunction(0).accept(this);
        a.append(" if ");
        ctx.disjunction(1).accept(this);
        a.append(" else ");
        ctx.expression().accept(this);
        return null;
    }

    @Override
    public Void visitDisjunctionExpression(DisjunctionExpressionContext ctx) {
        return ctx.disjunction().accept(this);
    }

    @Override
    public Void visitLambdaExpression(LambdaExpressionContext ctx) {
        var lambdaHeader = ctx.lambdaHeader();
        var lambdaParameters = lambdaHeader.lambdaParameters();
        var parameters = lambdaHeader.parameters();
        boolean hasLParen = lambdaHeader.LPAREN() != null && lambdaParameters == null && parameters != null;
        boolean isAsync = lambdaHeader.ASYNC() != null;
        var retType = lambdaHeader.retType();
        if (hasLParen || isAsync || retType != null) {
            final String name = syntheticLambdaName();
            {
                var a = this.beforeStatement();
                var that = new Transpiler(a, this.syntheticLambdaCount, this.syntheticClassCount);
                that.newStatement();
                if (isAsync) {
                    a.append("async def ");
                } else {
                    a.append("def ");
                }
                a.append(name).append('(');
                if (parameters != null) {
                    parameters.accept(that);
                } else if (lambdaParameters != null) {
                    lambdaParameters.accept(that);
                }
                a.append(')');
                if (retType != null) {
                    retType.accept(that);
                }
                a.append(": return ");
                ctx.expression().accept(that);
                a.newline();
                that.endStatement();
                this.syntheticLambdaCount = that.syntheticLambdaCount;
                this.syntheticClassCount  = that.syntheticClassCount;
            }
            a.append(name);
        } else {
            if (lambdaParameters != null) {
                a.append("lambda ");
                lambdaParameters.accept(this);
                a.append(": ");
            } else {
                a.append("lambda: ");
            }
            ctx.expression().accept(this);
        }
        return null;
    }

    @Override
    public Void visitMultiLineLambdaExpression(MultiLineLambdaExpressionContext ctx) {
        var lambdaHeader = ctx.lambdaHeader();
        boolean hasLParen = lambdaHeader.LPAREN() != null;
        boolean isAsync = lambdaHeader.ASYNC() != null;
        var retType = lambdaHeader.retType();
        var funcBody = ctx.funcBody();
        var funcBodyStatements = funcBody.statement();
        StatementContext onlyStmt = null;
        if (!(hasLParen || isAsync) && retType == null && (funcBodyStatements.isEmpty() || funcBodyStatements.size() == 1 && ((onlyStmt = funcBodyStatements.get(0)) instanceof ReturnStatementContext || onlyStmt instanceof EmptyStatementContext))) {
            a.append('(');
            var lambdaParameters = lambdaHeader.lambdaParameters();
            if (lambdaParameters != null) {
                a.append("lambda ");
                lambdaParameters.accept(this);
                a.append(": ");
            } else {
                a.append("lambda: ");
            }
            if (onlyStmt instanceof ReturnStatementContext retStmt) {
                var starExpressions = retStmt.starExpressions();
                if (starExpressions != null) {
                    if (starExpressions.COMMA(0) != null) {
                        a.append('(');
                        starExpressions.accept(this);
                        a.append(')');
                    } else {
                        starExpressions.accept(this);
                    }
                } else {
                    a.append("None");
                }
            } else {
                a.append("None");
            }
            a.append(')');
            return null;
        }
        final String name = syntheticLambdaName();
        {
            var a = this.beforeStatement();
            var that = new Transpiler(a, this.syntheticLambdaCount, this.syntheticClassCount);
            that.newStatement();
            if (lambdaHeader.ASYNC() != null) {
                a.append("async def ");
            } else {
                a.append("def ");
            }
            a.append(name).append('(');
            var parameters = lambdaHeader.parameters();
            var lambdaParameters = lambdaHeader.lambdaParameters();
            if (parameters != null) {
                parameters.accept(that);
            } else if (lambdaParameters != null) {
                lambdaParameters.accept(that);
            }
            a.append(')');
            if (retType != null) {
                retType.accept(that);
            }
            funcBody.accept(that);
            that.endStatement();
            this.syntheticLambdaCount = that.syntheticLambdaCount;
            this.syntheticClassCount  = that.syntheticClassCount;
        }
        a.append(name);
        return null;
    }

    @Override
    public Void visitLambdaHeader(LambdaHeaderContext ctx) {
        throw new RuntimeException();
    }

    @Override
    public Void visitLambdaParameters(LambdaParametersContext ctx) {
        var slashNoDefault = ctx.lambdaSlashNoDefault();
        var paramsNoDefault = ctx.lambdaParamsNoDefault();
        var paramsWithDefault = ctx.lambdaParamsWithDefault();
        var slashWithDefault = ctx.lambdaSlashWithDefault();
        var starEtc = ctx.lambdaStarEtc();
        if (slashNoDefault != null) {
            slashNoDefault.accept(this);
            if (paramsNoDefault != null) {
                a.append(", ");
                paramsNoDefault.accept(this);
            }
            if (paramsWithDefault != null) {
                a.append(", ");
                paramsWithDefault.accept(this);
            }
            if (starEtc != null) {
                a.append(", ");
                starEtc.accept(this);
            }
        } else if (slashWithDefault != null) {
            slashWithDefault.accept(this);
            if (paramsWithDefault != null) {
                a.append(", ");
                paramsWithDefault.accept(this);
            }
            if (starEtc != null) {
                a.append(", ");
                starEtc.accept(this);
            }
        } else if (paramsNoDefault != null) {
            paramsNoDefault.accept(this);
            if (paramsWithDefault != null) {
                a.append(", ");
                paramsWithDefault.accept(this);
            }
            if (starEtc != null) {
                a.append(", ");
                starEtc.accept(this);
            }
        } else if (paramsWithDefault != null) {
            paramsWithDefault.accept(this);
            if (starEtc != null) {
                a.append(", ");
                starEtc.accept(this);
            }
        } else {
            assert starEtc != null;
            starEtc.accept(this);
        }
        return null;
    }

    @Override
    public Void visitLambdaSlashNoDefault(LambdaSlashNoDefaultContext ctx) {
        ctx.lambdaParamsNoDefault().accept(this);
        a.append(", /");
        return null;
    }

    @Override
    public Void visitLambdaSlashWithDefault(LambdaSlashWithDefaultContext ctx) {
        var iter1 = ctx.lambdaParamNoDefault().iterator();
        var iter2 = ctx.lambdaParamWithDefault().iterator();
        if (iter1.hasNext()) {
            iter1.next().accept(this);
            while (iter1.hasNext()) {
                a.append(", ");
                iter1.next().accept(this);
            }
        } else {
            iter2.next().accept(this);
        }
        while (iter2.hasNext()) {
            a.append(", ");
            iter2.next().accept(this);
        }
        a.append(", /");
        return null;
    }

    @Override
    public Void visitLambdaParamsNoDefault(LambdaParamsNoDefaultContext ctx) {
        var iter = ctx.lambdaParamNoDefault().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitLambdaParamNoDefault(LambdaParamNoDefaultContext ctx) {
        a.append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitLambdaParamsWithDefault(LambdaParamsWithDefaultContext ctx) {
        var iter = ctx.lambdaParamWithDefault().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitLambdaParamWithDefault(LambdaParamWithDefaultContext ctx) {
        a.append(ctx.identifier().getText());
        ctx.defaultVal().accept(this);
        return null;
    }

    @Override
    public Void visitLambdaParamMaybeDefault(LambdaParamMaybeDefaultContext ctx) {
        a.append(ctx.identifier().getText());
        var defaultVal = ctx.defaultVal();
        if (defaultVal != null) {
            defaultVal.accept(this);
        }
        return null;
    }

    @Override
    public Void visitLambdaStarEtc(LambdaStarEtcContext ctx) {
        var paramNoDefault = ctx.lambdaParamNoDefault();
        var iter = ctx.lambdaParamMaybeDefault().iterator();
        var kwds = ctx.lambdaKwds();
        if (paramNoDefault != null) {
            a.append('*');
            paramNoDefault.accept(this);
            while (iter.hasNext()) {
                a.append(", ");
                iter.next().accept(this);
            }
            if (kwds != null) {
                a.append(", ");
                kwds.accept(this);
            }
        } else if (iter.hasNext()) {
            a.append('*');
            do {
                a.append(", ");
                iter.next().accept(this);
            } while (iter.hasNext());
            if (kwds != null) {
                a.append(", ");
                kwds.accept(this);
            }
        } else {
            assert kwds != null;
            kwds.accept(this);
        }
        return null;
    }

    @Override
    public Void visitLambdaKwds(LambdaKwdsContext ctx) {
        a.append("**");
        ctx.lambdaParamNoDefault().accept(this);
        return null;
    }

    @Override
    public Void visitDisjunction(DisjunctionContext ctx) {
        var iter = ctx.conjunction().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(" or ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitConjunction(ConjunctionContext ctx) {
        var iter = ctx.inversion().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(" and ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitInversion(InversionContext ctx) {
        var inversion = ctx.inversion();
        if (inversion != null) {
            a.append("not ");
            inversion.accept(this);
        } else {
            ctx.comparison().accept(this);
        }
        return null;
    }

    @Override
    public Void visitComparison(ComparisonContext ctx) {
        ctx.bitwiseOr().accept(this);
        var iter = ctx.compareOpBitwiseOrPair().iterator();
        while (iter.hasNext()) {
            a.append(' ');
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitCompareOpBitwiseOrPair(CompareOpBitwiseOrPairContext ctx) {
        ctx.compareOp().accept(this);
        a.append(' ');
        ctx.bitwiseOr().accept(this);
        return null;
    }

    @Override
    public Void visitCompareOp(CompareOpContext ctx) {
        var not = ctx.NOT();
        var in = ctx.IN();
        if (not != null) {
            if (in != null) {
                a.append("not in");
            } else {
                a.append("is not");
            }
        } else {
            a.append(ctx.getText());
        }
        return null;
    }

    @Override
    public Void visitBitwiseOr(BitwiseOrContext ctx) {
        var bitwiseOr = ctx.bitwiseOr();
        if (bitwiseOr != null) {
            bitwiseOr.accept(this);
            a.append(" | ");
        }
        ctx.bitwiseXor().accept(this);
        return null;
    }

    @Override
    public Void visitBitwiseXor(BitwiseXorContext ctx) {
        var bitwiseXor = ctx.bitwiseXor();
        if (bitwiseXor != null) {
            bitwiseXor.accept(this);
            a.append(" ^ ");
        }
        ctx.bitwiseAnd().accept(this);
        return null;
    }

    @Override
    public Void visitBitwiseAnd(BitwiseAndContext ctx) {
        var bitwiseAnd = ctx.bitwiseAnd();
        if (bitwiseAnd != null) {
            bitwiseAnd.accept(this);
            a.append(" & ");
        }
        ctx.shiftExpr().accept(this);
        return null;
    }

    @Override
    public Void visitShiftExpr(ShiftExprContext ctx) {
        var shiftExpr = ctx.shiftExpr();
        if (shiftExpr != null) {
            shiftExpr.accept(this);
            a.append(' ').append(ctx.shiftOp().getText()).append(' ');
        }
        ctx.sum().accept(this);
        return null;
    }

    @Override
    public Void visitShiftOp(ShiftOpContext ctx) {
        a.append(ctx.getText());
        return null;
    }

    @Override
    public Void visitSum(SumContext ctx) {
        var sum = ctx.sum();
        if (sum != null) {
            sum.accept(this);
            a.append(' ').append(ctx.sumOp().getText()).append(' ');
        }
        ctx.term().accept(this);
        return null;
    }

    @Override
    public Void visitSumOp(SumOpContext ctx) {
        a.append(ctx.getText());
        return null;
    }

    @Override
    public Void visitTerm(TermContext ctx) {
        var term = ctx.term();
        if (term != null) {
            term.accept(this);
            a.append(' ').append(ctx.termOp().getText()).append(' ');
        }
        ctx.factor().accept(this);
        return null;
    }

    @Override
    public Void visitTermOp(TermOpContext ctx) {
        a.append(ctx.getText());
        return null;
    }

    @Override
    public Void visitFactor(FactorContext ctx) {
        var prefixOp = ctx.prefixOp();
        if (prefixOp != null) {
            a.append(prefixOp.getText());
            ctx.factor().accept(this);
        } else {
            ctx.power().accept(this);
        }
        return null;
    }

    @Override
    public Void visitPrefixOp(PrefixOpContext ctx) {
        a.append(ctx.getText());
        return null;
    }

    @Override
    public Void visitPower(PowerContext ctx) {
        ctx.awaitPrimary().accept(this);
        var factor = ctx.factor();
        if (factor != null) {
            a.append(" ** ");
            factor.accept(this);
        }
        return null;
    }

    @Override
    public Void visitAwaitPrimary(AwaitPrimaryContext ctx) {
        if (ctx.AWAIT() != null) {
            a.append("await ");
        }
        ctx.primary().accept(this);
        return null;
    }

    @Override
    public Void visitPropertyPrimary(PropertyPrimaryContext ctx) {
        ctx.primary().accept(this);
        a.append('.').append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitCallWithGenExpPrimary(CallWithGenExpPrimaryContext ctx) {
        ctx.primary().accept(this);
        ctx.genExp().accept(this);
        return null;
    }

    @Override
    public Void visitCallPrimary(CallPrimaryContext ctx) {
        ctx.primary().accept(this);
        a.append('(');
        var arguments = ctx.arguments();
        if (arguments != null) {
            arguments.accept(this);
        }
        a.append(')');
        return null;
    }

    @Override
    public Void visitSlicePrimary(SlicePrimaryContext ctx) {
        ctx.primary().accept(this);
        a.append('[');
        ctx.slices().accept(this);
        a.append(']');
        return null;
    }

    @Override
    public Void visitAtomPrimary(AtomPrimaryContext ctx) {
        return ctx.atom().accept(this);
    }

    @Override
    public Void visitIdentifier(IdentifierContext ctx) {
        a.append(ctx.getText());
        return null;
    }

    @Override
    public Void visitAugAssign(AugAssignContext ctx) {
        a.append(' ').append(ctx.getText()).append(' ');
        return null;
    }

    @Override
    public Void visitPropertyTPrimary(PropertyTPrimaryContext ctx) {
        ctx.tPrimary().accept(this);
        a.append('.').append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitSliceTPrimary(SliceTPrimaryContext ctx) {
        ctx.tPrimary().accept(this);
        a.append('[');
        ctx.slices().accept(this);
        a.append(']');
        return null;
    }

    @Override
    public Void visitCallWithGenExpTPrimary(CallWithGenExpTPrimaryContext ctx) {
        ctx.tPrimary().accept(this);
        ctx.genExp().accept(this);
        return null;
    }

    @Override
    public Void visitCallTPrimary(CallTPrimaryContext ctx) {
        ctx.tPrimary().accept(this);
        a.append('(');
        var arguments = ctx.arguments();
        if (arguments != null) {
            arguments.accept(this);
        }
        a.append(')');
        return null;
    }

    @Override
    public Void visitAtomTPrimary(AtomTPrimaryContext ctx) {
        return ctx.atom().accept(this);
    }

    @Override
    public Void visitSlices(SlicesContext ctx) {
        var iter = ctx.slice().iterator();
        iter.next().accept(this);
        if (iter.hasNext()) {
            do {
                a.append(", ");
                iter.next().accept(this);
            } while (iter.hasNext());
        } else if (ctx.COMMA(0) != null) {
            a.append(',');
        }
        return null;
    }

    @Override
    public Void visitSlice(SliceContext ctx) {
        var namedExpression = ctx.namedExpression();
        if (namedExpression != null) {
            namedExpression.accept(this);
        } else {
            if (ctx.begin != null) {
                ctx.begin.accept(this);
            }
            a.append(':');
            if (ctx.end != null) {
                a.append(':');
            }
            if (ctx.COLON().size() == 2) {
                a.append(':');
                if (ctx.step != null) {
                    ctx.step.accept(this);
                }
            }
        }
        return null;
    }

    @Override
    public Void visitNamedAtom(NamedAtomContext ctx) {
        a.append(ctx.identifier().getText());
        return null;
    }

    @Override
    public Void visitTrueAtom(TrueAtomContext ctx) {
        a.append("True");
        return null;
    }

    @Override
    public Void visitFalseAtom(FalseAtomContext ctx) {
        a.append("False");
        return null;
    }

    @Override
    public Void visitNoneAtom(NoneAtomContext ctx) {
        a.append("None");
        return null;
    }

    @Override
    public Void visitStringsAtom(StringsAtomContext ctx) {
        return ctx.strings().accept(this);
    }

    @Override
    public Void visitNumberAtom(NumberAtomContext ctx) {
        a.append(ctx.NUMBER().getText());
        return null;
    }

    @Override
    public Void visitTupleAtom(TupleAtomContext ctx) {
        a.append('(');
        var starNamedExpressions = ctx.starNamedExpressions();
        if (starNamedExpressions != null) {
            starNamedExpressions.accept(this);
        }
        // var iter = ctx.starNamedExpression().iterator();
        // if (iter.hasNext()) {
        //     iter.next().accept(this);
        //     if (iter.hasNext()) {
        //         do {
        //             a.append(", ");
        //             iter.next().accept(this);
        //         } while (iter.hasNext());
        //     } else {
        //         a.append(',');
        //     }
        // }
        a.append(')');
        return null;
    }

    @Override
    public Void visitGroupAtom(GroupAtomContext ctx) {
        a.append('(');
        ctx.getChild(1).accept(this);
        a.append(')');
        return null;
    }

    @Override
    public Void visitGenExp(GenExpContext ctx) {
        a.append('(');
        ctx.getChild(1).accept(this);
        a.append(' ');
        ctx.forIfClauses().accept(this);
        a.append(')');
        return null;
    }

    @Override
    public Void visitForIfClauses(ForIfClausesContext ctx) {
        var iter = ctx.forIfClause().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(' ');
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitForIfClause(ForIfClauseContext ctx) {
        if (ctx.ASYNC() != null) {
            a.append("async for ");
        } else {
            a.append("for ");
        }
        getTupleCond(ctx.starTargets()).accept(this);
        a.append(" in ");
        getCond(ctx.disjunction()).accept(this);
        for (var filter : ctx.filter()) {
            a.append(' ');
            filter.accept(this);
        }
        return null;
    }

    @Override
    public Void visitFile(FileContext ctx) {
        for (var statement : ctx.statement()) {
            statement.accept(this);
        }
        return null;
    }

    @Override
    public Void visitFilter(FilterContext ctx) {
        a.append("if ");
        ctx.disjunction().accept(this);
        return null;
    }

    @Override
    public Void visitComplexNumber(ComplexNumberContext ctx) {
        ctx.signedRealNumber().accept(this);
        a.append(ctx.sign().getText());
        ctx.imaginaryNumber().accept(this);
        return null;
    }

    @Override
    public Void visitSign(SignContext ctx) {
        a.append(ctx.getText());
        return null;
    }

    @Override
    public Void visitRealNumber(RealNumberContext ctx) {
        a.append(ctx.NUMBER().getText());
        return null;
    }

    @Override
    public Void visitSignedRealNumber(SignedRealNumberContext ctx) {
        if (ctx.MINUS() != null) {
            a.append('-');
        }
        ctx.realNumber().accept(this);
        return null;
    }

    @Override
    public Void visitSignedNumber(SignedNumberContext ctx) {
        if (ctx.MINUS() != null) {
            a.append('-');
        }
        a.append(ctx.NUMBER().getText());
        return null;
    }

    @Override
    public Void visitImaginaryNumber(ImaginaryNumberContext ctx) {
        a.append(ctx.NUMBER().getText());
        return null;
    }

    @Override
    public Void visitListAtom(ListAtomContext ctx) {
        a.append('[');
        var starNamedExpressions = ctx.starNamedExpressions();
        if (starNamedExpressions != null) {
            starNamedExpressions.accept(this);
        }
        a.append(']');
        return null;
    }

    @Override
    public Void visitListCompAtom(ListCompAtomContext ctx) {
        a.append('[');
        ctx.namedExpression().accept(this);
        a.append(' ');
        ctx.forIfClauses().accept(this);
        a.append(']');
        return null;
    }

    @Override
    public Void visitDictAtom(DictAtomContext ctx) {
        var doubleStarredKVPairs = ctx.doubleStarredKVPairs();
        if (doubleStarredKVPairs != null) {
            a.append("{ ");
            doubleStarredKVPairs.accept(this);
            a.append(" }");
        } else {
            a.append("{}");
        }
        return null;
    }

    @Override
    public Void visitDoubleStarredKVPairs(DoubleStarredKVPairsContext ctx) {
        var iter = ctx.doubleStarredKVPair().iterator();
        iter.next().accept(this);
        while (iter.hasNext()) {
            a.append(", ");
            iter.next().accept(this);
        }
        return null;
    }

    @Override
    public Void visitDoubleStarredKVPair(DoubleStarredKVPairContext ctx) {
        var kvPair = ctx.kVPair();
        if (kvPair != null) {
            kvPair.accept(this);
        } else {
            a.append("**");
            ctx.bitwiseOr().accept(this);
        }
        return null;
    }

    @Override
    public Void visitDictCompAtom(DictCompAtomContext ctx) {
        a.append("{ ");
        ctx.kVPair().accept(this);
        a.append(' ');
        ctx.forIfClauses().accept(this);
        a.append(" }");
        return null;
    }

    @Override
    public Void visitKVPair(KVPairContext ctx) {
        ctx.key.accept(this);
        a.append(": ");
        ctx.value.accept(this);
        return null;
    }

    @Override
    public Void visitAnonymousClassExpression(AnonymousClassExpressionContext ctx) {
        var genExp = ctx.genExp();
        final String name;
        name = syntheticClassName(getFirstArgumentIdentifier(ctx.superClassArgs));
        {
            var a = this.beforeStatement();
            var that = new Transpiler(a, syntheticLambdaCount, syntheticClassCount);
            a.append("def ").append(name).append("():").incrIndent().newline();
            that.newStatement();
            a.append("class ").append(name);
            boolean hasParens;
            if (ctx.superClassArgs != null) {
                hasParens = true;
            } else if (genExp != null) {
                hasParens = ctx.LPAREN(0) != null;
            } else {
                hasParens = ctx.LPAREN(1) != null;
            }
            if (hasParens) {
                a.append('(');
                if (ctx.superClassArgs != null) {
                    ctx.superClassArgs.accept(that);
                }
                a.append(')');
            }
            ctx.classBody().accept(that);
            that.endStatement();
            a.append("return ").append(name).decrIndent().newline();
            this.syntheticLambdaCount = that.syntheticLambdaCount;
            this.syntheticClassCount  = that.syntheticClassCount;
        }
        a.append(name).append("()");
        if (genExp != null) {
            genExp.accept(this);
        } else {
            a.append('(');
            if (ctx.constructorArgs != null) {
                ctx.constructorArgs.accept(this);
            }
            a.append(')');
        }
        return null;
    }

    @Override
    public Void visitEllipsisAtom(EllipsisAtomContext ctx) {
        a.append("...");
        return null;
    }

    @Override
    public Void visitExpressions(ExpressionsContext ctx) {
        var iter = ctx.expression().iterator();
        iter.next().accept(this);
        if (iter.hasNext()) {
            do {
                a.append(", ");
                iter.next().accept(this);
            } while (iter.hasNext());
        } else if (ctx.COMMA(0) != null) {
            a.append(',');
        }
        return null;
    }

    @Override
    public Void visitGenExpAtom(GenExpAtomContext ctx) {
        return ctx.genExp().accept(this);
    }
}
