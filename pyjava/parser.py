from typing import Iterable, List, Union, Optional, Dict, Set, Callable, Any, Tuple
import functools
from enum import Enum
from pyjava.tokenize import *
import pyjava.tree as tree
from pyjava.util import *

__all__ = ["PyJavaParser", "parse_file", "parse_str"]

class PyJavaParser:
    def __init__(self, tokens: Iterable[TokenInfo], filename="<unknown source>"):
        tokens = list(tokens)
        #region merge tokens
        i = 0
        def merge(val: str):
            start = tokens[i].start
            end = tokens[i+1].end
            line = tokens[i].line
            newtoken = TokenInfo(OP, val, start, end, line)
            del tokens[i+1]
            tokens[i] = newtoken
        def testdouble(a: str, b: str):
            return tokens[i].string == a and tokens[i+1].string == b \
                    and tokens[i].end[0] == tokens[i+1].start[0]
        while i < len(tokens) - 1:
            if testdouble('not', 'in'):
                merge('not in')
            elif testdouble('is', 'not'):
                merge('is not')
            i += 1
        #endregion merge tokens

        self.tokens = LookAheadListIterator(tokens)
        self.filename = filename
        self.scope = ParserScope(self)
        if self.token.type == ENCODING:
            next(self.tokens)
        while self.token.type == COMMENT:
            next(self.tokens)

        self._bdyctxtmgr = BodyStatementContextManager()
        self._exprctxtmgr = ExpressionContextManager()
        self._bdyANDexprctxtmgr = MultiContextManager(self._bdyctxtmgr, self._exprctxtmgr)

    def append_before_stmt(self, func: Callable[[], Union[tree.Statement, List[tree.Statement], Tuple[Union[tree.Statement, List[tree.Statement]], Union[tree.Statement, List[tree.Statement]]]]]):
        stmts, to_do = self._bdyctxtmgr._stack[-1]
        to_do.append((len(stmts), func))

    def enter_block(self, stmts: List[tree.Statement]):
        self._bdyctxtmgr._stack.append((stmts, []))
        return self._bdyctxtmgr
        
    def enter_comprehension(self):
        self._exprctxtmgr._contexts.append(ExpressionContext.COMPREHENSION)
        return self._exprctxtmgr

    def enter_brace_block(self):
        self._exprctxtmgr._contexts.append(ExpressionContext.NORMAL)
        return self._exprctxtmgr

    @property
    def walrus_allowed_here(self):
        return self._exprctxtmgr._contexts[-1] is ExpressionContext.NORMAL

    #region traversal functions

    @property
    def token(self):
        return self.tokens.look(0)

    def next_token(self):
        next(self.tokens)
        while self.token.type == COMMENT:
            next(self.tokens)

    def tok_match(self, token, test):
        if isinstance(test, (tuple, set, list)):
            for subtest in test:
                if self.tok_match(token, subtest):
                    return True
            return False
        elif isinstance(test, str):
            return token.string == test
        elif isinstance(test, int):
            return token.exact_type == test #or test == NEWLINE and token.string == ';' # or test in (NEWLINE, DEDENT) and token.type == ENDMARKER
        else:
            raise TypeError(f"invalid test: {test!r}")

    def accept(self, *tests):
        self.tokens.push_marker()
        last = None
        for test in tests:
            if not self.tok_match(self.token, test):
                self.tokens.pop_marker(reset=True)
                return None
            last = self.token.string
            self.next_token()
    
        if last == '': last = True
        self.tokens.pop_marker(reset=False)
        return last

    def would_accept(self, *tests):
        look = 0
        for test in tests:
            token = self.tokens.look(look)

            if not self.tok_match(token, test):
                return False

            look += 1

        return True

    def _test_str(self, test):
        if isinstance(test, (tuple, set)):
            return join_natural((self._test_str(x) for x in test), word='or')
        elif isinstance(test, int):
            return tok_name[test]
        elif isinstance(test, str):
            return repr(test)
        else:
            raise TypeError(f'invalid test: {test!r}')

    def require(self, *tests):
        result = self.accept(*tests)
        if not result:
            raise SyntaxError(f'expected {" ".join(self._test_str(x) for x in tests)}', self.position)
        return result
    
    @property
    def position(self):
        """ Returns a tuple of (filename, line#, column#, line) """
        return (self.filename, *self.token.start, self.token.line)

    #endregion traversal functions

    #region parsing helper functions

    def parse_listof(self, parsemethod: Callable[[], Any], sep=[','], endif=None) -> list:
        result = [parsemethod()]
        if not isinstance(sep, list):
            sep = [sep]
        if endif is not None and not isinstance(endif, list):
            endif = [endif]
        while self.accept(*sep):
            if endif is not None and self.would_accept(*endif):
                break
            result.append(parsemethod())
        return result

    def parse_if_accept(self, parsemethod: Callable[[], Any], *tests):
        if self.accept(*tests):
            return parsemethod()

    def parse_commalist(self, parsemethod: Callable[[], Any], endif=None):
        result = parsemethod()
        if not self.accept(','):
            return result
        result = [result]
        if endif is not None and not isinstance(endif, list):
            endif = [endif]
        if not self.would_accept(*endif):
            result.append(parsemethod())
            while self.accept(','):
                if self.would_accept(*endif):
                    break
                result.append(parsemethod())
        return tree.CommaExpression(result)

    def parse_binary_expr(self, parsemethod: Callable[[], Any], ops: Union[Tuple[str, ...], List[str], Set[str], str]):
        expr = parsemethod()
        result = self.accept(ops)
        while result:
            expr = tree.BinaryExpression(expr, result, parsemethod())
            result = self.accept(ops)
        return expr

    def syntheticname(self, name: str):
        i = 0
        name = '__' + name
        nname = name + str(i)
        while nname in self.scope:
            i += 1
            nname = name + str(i)
        return tree.Name(nname)

    #endregion parsing helper functions

    def parse_file(self) -> List[tree.Statement]:
        stmts = []
        with self.enter_block(stmts):
            while self.token.type != ENDMARKER:
                stmts.append(self.parse_stmt())
        return stmts

    def parse_ident(self) -> str:
        return self.require(NAME)

    def parse_name(self) -> tree.Name:
        return tree.Name(self.parse_ident())

    def parse_dotted_name(self) -> tree.DottedName:
        return tree.DottedName(self.parse_listof(self.parse_name, sep='.'))

    def parse_module_name(self) -> tree.ModuleName:
        prefix_dots = 0
        while True:
            if self.accept('...'):
                prefix_dots += 3
            elif self.accept('.'):
                prefix_dots += 1
            else:
                break
        if prefix_dots == 0 or self.token.exact_type == NAME:
            names = self.parse_listof(self.parse_name, sep='.')
        else:
            names = []
        return tree.ModuleName(names, prefix_dots)

    #region statement

    def parse_stmt(self):
        if self.token.type == KEYWORD:
            attr = f'parse_{self.token.string}_stmt'
            if hasattr(self, attr):
                return getattr(self, attr)()
        elif self.accept(';'):
            return tree.PassStatement()
        elif self.token.string == '@':
            return self.parse_decorated()
        return self.parse_expr_stmt()

    #region simple_stmt

    def parse_expr_stmt(self):
        expr = self.parse_testlist_star_expr(allow_walrus=False)
        if self.accept(':'):
            annotation = self.parse_test(allow_walrus=False)
            self.declvars(expr)
            if self.accept('='):
                value = self.parse_test(allow_walrus=False)
            else:
                value = None
            self.require(';')
            return tree.AnnotatedAssignmentStatement(expr, annotation, value)
        elif self.accept('='):
            self.declvars(expr)
            assigned = [expr]
            if self.token.string == 'yield':
                value = self.parse_yield_expr()
            else:
                value = self.parse_testlist_star_expr(allow_walrus=False)
                while self.accept('='):
                    self.declvars(value)
                    assigned.append(value)
                    if self.token.string == 'yield':
                        value = self.parse_yield_expr()
                        break
                    else:
                        value = self.parse_testlist_star_expr()
            self.require(';')
            return tree.MultiAssignmentStatement(assigned, value)
        else:
            self.require(';')
            return tree.ExpressionStatement(expr)

    def parse_del_stmt(self):
        self.require('del')
        exprs = self.parse_exprlist()
        self.require(';')
        exprs = exprs.items if isinstance(exprs, tree.CommaExpression) else [exprs]
        for expr in exprs:
            self.delvars(expr)
        return tree.DelStatement(exprs)

    def parse_pass_stmt(self):
        self.require('pass', ';')
        return tree.PassStatement()

    def parse_break_stmt(self):
        self.require('break', ';')
        return tree.BreakStatement()

    def parse_continue_stmt(self):
        self.require('continue', ';')
        return tree.ContinueStatement()

    def parse_return_stmt(self):
        self.require('return')
        if self.accept(';'):
            return tree.ReturnStatement()
        expr = self.parse_testlist()
        self.require(';')
        return tree.ReturnStatement(expr)

    def parse_yield_stmt(self):
        expr = self.parse_yield_expr()
        self.require(';')
        return tree.ExpressionStatement(expr)

    def parse_raise_stmt(self):
        self.require('raise')
        if self.accept(';'):
            return tree.RaiseStatement()
        error = self.parse_test()
        from_expr = self.parse_if_accept(self.parse_test, 'from')
        self.require(';')
        return tree.RaiseStatement(error, from_expr)

    def parse_import_stmt(self):
        self.require('import')
        imports = self.parse_listof(self.parse_dotted_name)
        self.require(';')
        return tree.ImportStatement(imports)

    def parse_dotted_as_name(self):
        name = self.parse_dotted_name()
        alias = self.parse_if_accept(self.parse_name, 'as')
        if alias:
            self.declvars(alias)
        else:
            self.declvars(name[0])
        return tree.DottedImportName(name, alias)
    
    def parse_from_stmt(self):
        self.require('from')
        module = self.parse_module_name()
        self.require('import')
        if self.accept('*'):
            imports = []
        else:
            imports = self.parse_listof(self.parse_as_name)
        self.require(';')
        return tree.ImportFromStatement(module, imports)

    def parse_as_name(self):
        name = self.parse_name()
        alias = self.parse_if_accept(self.parse_name, 'as')
        if alias:
            self.declvars(alias)
        else:
            self.declvars(name)
        return tree.ImportName(name, alias)

    def parse_global_stmt(self):
        self.require('global')
        names = self.parse_listof(self.parse_name)
        self.require(';')
        for name in names:
            self.declvars(name)
        return tree.GlobalStatement(names)

    def parse_nonlocal_stmt(self):
        self.require('nonlocal')
        names = self.parse_listof(self.parse_name)
        self.require(';')
        for name in names:
            self.declvars(name)
        return tree.NonLocalStatement(names)

    def parse_assert_stmt(self):
        self.require('assert')
        test = self.parse_test()
        msg = self.parse_if_accept(self.parse_test, ',')
        self.require(';')
        return tree.AssertStatement(test, msg)

    #endregion simple_stmt

    #region compound_stmt

    def parse_async_stmt(self):
        self.require('async')
        if self.token.string == 'for':
            stmt = self.parse_for_stmt()
        elif self.token.string == 'with':
            stmt = self.parse_with_stmt()
        elif self.token.string == 'def':
            stmt = self.parse_def_stmt()
        else:
            raise SyntaxError("expected 'for', 'with', or 'def'", self.position)
        stmt.isasync = True
        return stmt

    def parse_decorated(self):
        decorators = [self.parse_decorator()]
        while self.token.string == '@':
            decorators.append(self.parse_decorator())
        if self.token.string == 'def':
            stmt = self.parse_def_stmt()
        elif self.token.string == 'async':
            self.next_token()
            stmt = self.parse_def_stmt()
            stmt.isasync = True
        elif self.token.string == 'class':
            stmt = self.parse_class_stmt()
        else:
            raise SyntaxError("expected 'class', 'def', or 'async def'", self.position)
        stmt.decorators.extend(decorators)
        return stmt

    def parse_decorator(self):
        self.require('@')
        name = self.parse_dotted_name()
        if self.accept('('):
            if self.accept(')'):
                args = []
            else:
                args = self.parse_arglist()
                self.require(')')
        else:
            args = []
        return tree.Decorator(name, args)

    def parse_if_stmt(self):
        self.require('if')
        condition = self.parse_test()
        body = self.parse_body()
        elifs = []
        while self.accept('elif'):
            elif_condition = self.parse_test()
            elif_body = self.parse_body()
            elifs.append(tree.Elif(elif_condition, elif_body))
        else_body = self.parse_if_accept(self.parse_body, 'else')
        return tree.IfStatement(condition, body, elifs, else_body)

    def parse_while_stmt(self):
        self.require('while')
        condition = self.parse_test()
        body = self.parse_body()
        else_body = self.parse_if_accept(self.parse_body, 'else')
        return tree.WhileStatement(condition, body, else_body)
    
    def parse_for_stmt(self):
        self.require('for')
        variables = self.parse_exprlist()
        self.declvars(variables)
        self.require('in')
        iterable = self.parse_testlist()
        body = self.parse_body()
        else_body = self.parse_if_accept(self.parse_body, 'else')
        return tree.ForStatement(variables, iterable, body, else_body)

    def parse_try_stmt(self):
        self.require('try')
        body = self.parse_body()
        if self.accept('finally'):
            return tree.TryStatement(body, finally_body=self.parse_body())
        excepts = [self.parse_except_clause()]
        else_body = self.parse_if_accept(self.parse_body, 'else')
        finally_body = self.parse_if_accept(self.parse_body, 'finally')
        return tree.TryStatement(body, excepts, else_body, finally_body)

    def parse_except_clause(self):
        self.require('except')
        if self.token.string in (':', '{'):
            return tree.ExceptClause(body=self.parse_body())
        test = self.parse_test()
        name = self.parse_if_accept(self.parse_name, 'as')
        if name:
            self.declvars(name)
        body = self.parse_body()
        return tree.ExceptClause(test=test, name=name, body=body)
    
    def parse_with_stmt(self):
        self.require('with')
        items = self.parse_listof(self.parse_with_item)
        body = self.parse_body()
        return tree.WithStatement(items, body)

    def parse_with_item(self):
        expr = self.parse_test()
        assigned = self.parse_if_accept(self.parse_expr, 'as')
        if assigned:
            self.declvars(assigned)
        return tree.WithItem(expr, assigned)

    def parse_def_stmt(self):
        self.require('def')
        name = self.parse_name()
        self.declvars(name)
        with self.scope:
            self.require('(')
            if self.accept(')'):
                parameters = []
            else:
                parameters = self.parse_typedargslist()
                self.require(')')
            annotation = self.parse_if_accept(self.parse_test, '->')
            body = self.parse_body()
        return tree.FunctionDefinition(name, parameters, annotation=annotation, body=body)

    def parse_class_stmt(self):
        self.require('class')
        name = self.parse_name()
        self.declvars(name)
        if self.accept('('):
            if self.accept(')'):
                args = []
            else:
                args = self.parse_arglist()
                self.require(')')
        else:
            args = []
        with self.scope:
            body = self.parse_body(is_class=True)
        return tree.ClassStatement(name, args, body)

    def parse_typedargslist(self):
        args = []
        if self.accept('**'):
            self.parse_typedargs_starstar_rest(args)
        elif self.accept('*'):
            self.parse_typedargs_star_rest(args)
        else:
            name = self.parse_name()
            annotation = self.parse_if_accept(functools.partial(self.parse_test, allow_walrus=False), ':')
            self.declvars(name)
            if self.accept('='):
                args.append(tree.NormalFunctionParameter(name, annotation, self.parse_test(allow_walrus=False)))
                self.parse_typedargs_default_rest(args)
            else:
                args.append(tree.NormalFunctionParameter(name, annotation))
                while self.accept(','):
                    if self.would_accept((':', '{', ')')):
                        break
                    if self.accept('*'):
                        self.parse_typedargs_star_rest(args)
                        break
                    if self.accept('**'):
                        self.parse_typedargs_starstar_rest(args)
                        break
                    name = self.parse_name()
                    annotation = self.parse_if_accept(functools.partial(self.parse_test, allow_walrus=False), ':')
                    self.declvars(name)
                    if self.accept('='):
                        args.append(tree.NormalFunctionParameter(name, annotation, self.parse_test(allow_walrus=False)))
                        self.parse_typedargs_default_rest(args)
                        break
                    args.append(tree.NormalFunctionParameter(name, annotation))
        return args

    def parse_typedargs_starstar_rest(self, args: List[tree.FunctionParameter]):
        name = self.parse_name()
        args.append(tree.StarStarFunctionParameter(name, self.parse_if_accept(self.parse_test, ':')))
        self.declvars(name)
        self.accept(',')

    def parse_typedargs_star_rest(self, args: List[tree.FunctionParameter]):
        if self.token.exact_type == NAME:
            name = self.parse_name()
            args.append(tree.StarFunctionParameter(name, self.parse_if_accept(self.parse_test, ':')))
            self.declvars(name)
        else:
            args.append(tree.StarFunctionParameter(None))
        while self.accept(','):
            if self.would_accept((':', '{', ')')):
                break
            if self.accept('**'):
                self.parse_typedargs_starstar_rest(args)
                break
            name = self.parse_name()
            annotation = self.parse_if_accept(self.parse_test, ':')
            self.declvars(name)
            default = self.parse_if_accept(self.parse_test(), '=')
            args.append(tree.NormalFunctionParameter(name, annotation, default))

    def parse_typedargs_default_rest(self, args: List[tree.FunctionParameter]):
        while self.accept(','):
            if self.would_accept((':', '{', ')')):
                break
            if self.accept('*'):
                self.parse_typedargs_star_rest(args)
                break
            if self.accept('**'):
                self.parse_typedargs_starstar_rest(args)
                break
            name = self.parse_name()
            annotation = self.parse_if_accept(functools.partial(self.parse_test, allow_walrus=False), ':')
            self.declvars(name)
            self.require('=')
            default = self.parse_test(allow_walrus=False)
            args.append(tree.NormalFunctionParameter(name, annotation, default))

    #endregion compound_stmt

    def parse_body(self, is_class=False):
        if self.accept('{'):
            stmts = []
            if self.accept('}'):
                stmts.append(tree.PassStatement())
            else:
                with self.enter_block(stmts):
                    stmts.append(self.parse_stmt())
                    while self.token.string != '}' and self.token.type != ENDMARKER:
                        stmts.append(self.parse_stmt())
                    self.require('}')
            return tree.Suite(stmts)
        else:
            self.require(':')
            stmts = []
            with self.enter_block(stmts):
                stmt = self.parse_stmt()
            if len(stmts) == 0:
                return stmt
            else:
                stmts.append(stmt)
                return tree.Suite(stmts)
    
    #endregion statement
    
    #region expressions
    
    def parse_exprlist(self):
        return self.parse_commalist(self.parse_expr_or_star_expr, endif=PyJavaParser.END_EXPR)

    def parse_expr_or_star_expr(self):
        if self.token.string == '*':
            return self.parse_star_expr()
        else:
            return self.parse_expr()

    def parse_star_expr(self):
        self.require('*')
        return tree.UnaryExpression(tree.UnaryOperator.STAR, self.parse_expr())

    def parse_testlist(self):
        return self.parse_commalist(self.parse_test, endif=PyJavaParser.END_EXPR)

    def parse_testlist_star_expr(self, allow_walrus=True):
        return self.parse_commalist(functools.partial(self.parse_test_or_star_expr, allow_walrus), endif=PyJavaParser.END_EXPR)

    def parse_test_or_star_expr(self, allow_walrus=True):
        if self.token.string == '*':
            return self.parse_star_expr()
        else:
            return self.parse_test(allow_walrus)

    def parse_test(self, allow_walrus=True):
        if self.token.string == 'lambda':
            return self.parse_lambda()
        if self.token.string == 'class':
            return self.parse_class_expr()
        if self.would_accept(NAME, ':='):
            name = self.parse_name()
            if allow_walrus and self.walrus_allowed_here:
                self.require(':=')
                value = self.parse_test(allow_walrus=False)
                self.declvars(name)
                return tree.AssignmentExpression(name, value)
            else:
                raise SyntaxError("walrus operator not allowed here", self.position)
        expr = self.parse_or_test()
        if not self.accept('if'):
            return expr
        condition = self.parse_or_test()
        self.require('else')
        elsepart = self.parse_test()
        return tree.IfExpression(expr, condition, elsepart)

    def parse_or_test(self):
        return self.parse_binary_expr(self.parse_and_test, 'or')

    def parse_and_test(self):
        return self.parse_binary_expr(self.parse_not_test, 'and')

    def parse_not_test(self):
        if self.accept('not'):
            return tree.UnaryExpression(tree.UnaryOperator.NOT, self.parse_not_test())
        else:
            return self.parse_comparison()

    def parse_comparison(self):
        return self.parse_binary_expr(self.parse_expr, ('<>', '<=', '>=', '<', '>', '==', '!=', 'in', 'not in', 'is', 'is not'))

    def parse_expr(self):
        return self.parse_or_expr()

    def parse_or_expr(self):
        return self.parse_binary_expr(self.parse_xor_expr, '|')

    def parse_xor_expr(self):
        return self.parse_binary_expr(self.parse_and_expr, '^')

    def parse_and_expr(self):
        return self.parse_binary_expr(self.parse_shift_expr, '&')

    def parse_shift_expr(self):
        return self.parse_binary_expr(self.parse_term, ('+', '-'))

    def parse_term(self):
        return self.parse_binary_expr(self.parse_factor, ('*', '/', '%', '//', '@'))
    
    def parse_factor(self):
        result = self.accept(('+', '-', '~'))
        if result:
            return tree.UnaryExpression(result, self.parse_factor())
        return self.parse_power()

    def parse_power(self):
        expr = self.parse_atom_expr()
        if self.accept('**'):
            return tree.BinaryExpression(expr, tree.BinaryOperator.POW, self.parse_factor())
        return expr

    def parse_atom_expr(self):
        if self.accept('await'):
            return tree.UnaryExpression(tree.UnaryOperator.AWAIT, self.parse_trailers(self.parse_atom()))
        return self.parse_trailers(self.parse_atom())

    def parse_trailers(self, expr: tree.Expression):
        while True:
            if self.accept('('):
                if self.accept(')'):
                    args = []
                else:
                    args = self.parse_arglist()
                    self.require(')')
                expr = tree.CallExpression(expr, args)
            elif self.accept('['):
                index = self.parse_commalist(self.parse_subscript, endif=']')
                self.require(']')
                expr = tree.IndexExpression(expr, index)
            elif self.accept('.'):
                name = self.parse_name()
                expr = tree.AttrExpression(expr, name)
            else:
                return expr

    def parse_arglist(self):
        if not self.would_accept(('*', '**')) and not self.would_accept(NAME, '='):
            test = self.parse_test()
            if self.would_accept(('async', 'for')):
                return [self.parse_generator_expr(test)]
            args = [test]
        else:
            args = [self.parse_argument()]
        while self.accept(','):
            if self.would_accept(')'):
                break
            args.append(self.parse_argument())
        return args

    def parse_argument(self):
        result = self.accept(('*', '**'))
        if result:
            return tree.UnaryExpression(result, self.parse_test(allow_walrus=False))
        elif self.would_accept(NAME, '='):
            name = self.parse_name()
            self.require('=')
            value = self.parse_test(allow_walrus=False)
            return tree.KeywordArgument(name, value)
        else:
            return self.parse_test()

    def parse_subscript(self):
        if self.accept(':'):
            if self.accept(':'):
                if self.would_accept((',', ']')):
                    return tree.SliceExpression()
                else:
                    return tree.SliceExpression(increment=self.parse_test())
            else:
                end = self.parse_test()
                if self.accept(':') and not self.would_accept((',', ']')):
                    increment = self.parse_test()
                else:
                    increment = None
                return tree.SliceExpression(end=end, increment=increment)
        else:
            start = self.parse_test()
            if self.accept(':'):
                if self.accept(':'):
                    if self.would_accept((',', ']')):
                        return tree.SliceExpression(start=start)
                    else:
                        return tree.SliceExpression(start=start, increment=self.parse_test())
                else:
                    end = self.parse_test()
                    if self.accept(':') and not self.would_accept((',', ']')):
                        increment = self.parse_test()
                    else:
                        increment = None
                    return tree.SliceExpression(start=start, end=end, increment=increment)
            else:
                return start

    def parse_generator_expr(self, expr: tree.Expression):
        while self.would_accept(('async', 'for')):
            isasync = bool(self.accept('async'))
            self.require('for')
            variables = self.parse_exprlist()
            self.declvars(variables)
            self.require('in')
            with self.enter_comprehension():
                iterable = self.parse_testlist()
                condition = self.parse_if_accept(self.parse_test, 'if')
            expr = tree.GeneratorExpression(expr, variables, iterable, condition, isasync)
        return expr

    def parse_atom(self):
        if self.accept('('):
            if self.accept(')'):
                expr = None
            else:
                if self.would_accept('yield'):
                    expr = self.parse_yield_expr()
                else:
                    expr = self.parse_testlist_comp()
                self.require(')')
            return tree.ParensExpression(expr)
        elif self.accept('['):
            if self.accept(']'):
                exprs = []
            else:
                expr = self.parse_testlist_comp()
                if isinstance(expr, tree.CommaExpression):
                    exprs = expr.items
                else:
                    exprs = [expr]
                self.require(']')
            return tree.ListLiteral(exprs)
        elif self.accept('{'):
            if self.accept('}'):
                return tree.MapLiteral([])
            if self.accept('**'):
                expr = tree.UnaryExpression(tree.UnaryOperator.STARSTAR, self.parse_expr())
                if self.would_accept(('async', 'for')):
                    entries = [self.parse_generator_expr(expr)]
                    self.accept(',')
                else:
                    entries = [expr]
                    while self.accept(','):
                        if self.token.string == '}':
                            break
                        if self.accept('**'):
                            expr = tree.UnaryExpression(tree.UnaryOperator.STARSTAR, self.parse_expr())
                        else:
                            expr = self.parse_test()
                            self.require(':')
                            expr = tree.MapEntry(expr, self.parse_test())
                        entries.append(expr)
                expr = tree.MapLiteral(entries) 
            else:
                expr = self.parse_test()
                if self.accept(':'):
                    expr = tree.MapEntry(expr, self.parse_test())
                    if self.would_accept(('async', 'for')):
                        entries = [self.parse_generator_expr(expr)]
                        self.accept(',')
                    else:
                        entries = [expr]
                        while self.accept(','):
                            if self.token.string == '}':
                                break
                            if self.accept('**'):
                                expr = tree.UnaryExpression(tree.UnaryOperator.STARSTAR, self.parse_expr())
                            else:
                                expr = self.parse_test()
                                self.require(':')
                                expr = tree.MapEntry(expr, self.parse_test())
                            entries.append(expr)
                    expr = tree.MapLiteral(entries)
                else:
                    elements = [expr]
                    while self.accept(','):
                        if self.token.string == '}':
                            break
                        elements.append(self.parse_test())
                    expr = tree.SetLiteral(elements)
            self.require('}')
            return expr
        elif self.accept('True'):
            return tree.BoolLiteral(True)
        elif self.accept('False'):
            return tree.BoolLiteral(False)
        elif self.accept('None'):
            return tree.NoneLiteral()
        elif self.accept(('...', 'Ellipsis')):
            return tree.EllipsisLiteral()
        elif self.token.type == STRING:
            strs = [self.token.string]
            self.next_token()
            while self.token.type == STRING:
                strs.append(self.token.string)
                self.next_token()
            return tree.StringLiteral(strs)
        elif self.token.type == NUMBER:
            val = self.token.string
            self.next_token()
            return tree.NumberLiteral(val)
        elif self.token.exact_type == NAME:
            return tree.VarExpression(self.parse_name())
        else:
            raise SyntaxError(f"unexpected token {simple_token_str(self.token)}", self.position)

    END_EXPR = (';', ')', ']', '}', ',', ':', ENDMARKER)
    END_FACTOR = (*END_EXPR, '*', '**', '^', '@', '|', 
        '~', '=', '==', '!=', '/', '//', '<=', '>=', '<', '>', '<>', 
        'is', 'is not', 'in', 'not in', '%', '+=', '-=', '*=', '/=', '%=',
        '//=', '@=', '^=', '|=', '&=', '<<=', '<<', '>>', '>>=')

    def parse_yield_expr(self):
        self.require('yield')
        if self.would_accept(PyJavaParser.END_FACTOR):
            return tree.YieldExpression()
        if self.accept('from'):
            return tree.YieldExpression(self.parse_test(), isfrom=True)
        else:
            return tree.YieldExpression(self.parse_testlist())

    def parse_testlist_comp(self):
        expr = self.parse_test_or_star_expr()
        if self.would_accept(('async', 'for')):
            return self.parse_generator_expr(expr)
        if self.accept(','):
            exprs = [expr]
            if not self.would_accept(PyJavaParser.END_EXPR):
                exprs.append(self.parse_test_or_star_expr())
                while self.accept(','):
                    if self.would_accept(PyJavaParser.END_EXPR):
                        break
                    exprs.append(self.parse_test_or_star_expr())
            return tree.CommaExpression(exprs)
        return expr
  
    def parse_class_expr(self):
        """ 'class' ['(' [arglist] ')'] '(' [arglist] ')' '{' {stmt} '}' """
        self.require('class')
        self.require('(')
        if self.accept(')'):
            args = []
        else:
            args = self.parse_arglist()
            self.require(')')
        if self.accept('('):
            clargs = args
            if self.accept(')'):
                args = []
            else:
                args = self.parse_arglist()
                self.require(')')
            if len(clargs) > 0:
                if isinstance(clargs[0], tree.VarExpression):
                    name = self.syntheticname(str(clargs[0].name))
                elif isinstance(clargs[0], tree.AttrExpression) and clargs[0].is_dotted_name:
                    name = self.syntheticname(str(clargs[0].attr))
                else:
                    name = self.syntheticname('object')
        else:
            clargs = []
            name = self.syntheticname('object')
        self.require('{')
        stmts = []
        if self.accept('}'):
            stmts.append(tree.PassStatement())
        else:
            with self.scope, self.enter_block(stmts):
                self.scope.current.add(str(name))
                stmts.append(self.parse_stmt())
                while self.token.string != '}' and self.token.type != ENDMARKER:
                    stmts.append(self.parse_stmt())
        self.require('}')
        body = tree.Suite(stmts)
        self.append_before_stmt(lambda: (tree.ClassStatement(name, clargs, body), tree.DelStatement([tree.VarExpression(name)])))
        return tree.CallExpression(tree.VarExpression(name), args)

    def parse_lambda(self):
        self.require('lambda')
        with self.scope:
            if self.would_accept((':', '{', '->')):
                parameters = []
                params_may_have_annotations = False
            else:
                if self.accept('('):
                    params_may_have_annotations = True
                    parameters = self.parse_typedargslist()
                    self.require(')')
                else:
                    params_may_have_annotations = False
                    parameters = self.parse_varargslist()
            if self.accept('->'):
                annotation = self.parse_test()
                requires_special = True
            else:
                annotation = None
                requires_special = False
            if not requires_special and params_may_have_annotations:
                for param in parameters:
                    if param.annotation:
                        requires_special = True
                        break
        if requires_special or self.token.string != ':':
            name = self.syntheticname('lambda')
            self.scope.current.add(str(name))
            body = self.parse_lambda_body()
            if not isinstance(body, tree.Suite):
                body = tree.Suite([tree.ReturnStatement(None if isinstance(body, tree.NoneLiteral) else body)])
            self.append_before_stmt(lambda: (tree.FunctionDefinition(name, parameters, annotation=annotation, body=body), tree.DelStatement([tree.VarExpression(name)])))
            return tree.VarExpression(name)
        body = self.parse_lambda_body()
        return tree.Lambda(parameters, body)

    def parse_lambda_body(self):
        if self.accept(':'):
            return self.parse_test(allow_walrus=False)
        self.require('{')
        stmts = []
        if self.accept('}'):
            stmts.append(tree.PassStatement())
        else:
            with self.enter_brace_block(), self.enter_block(stmts):
                stmts.append(self.parse_stmt())
                while self.token.string != '}' and self.token.type != ENDMARKER:
                    stmts.append(self.parse_stmt())
                self.require('}')
        return tree.Suite(stmts)

    #endregion expressions

    def parse_varargslist(self):
        args = []
        if self.accept('**'):
            self.parse_varargs_starstar_rest(args)
        elif self.accept('*'):
            self.parse_varargs_star_rest(args)
        else:
            name = self.parse_name()
            self.declvars(name)
            if self.accept('='):
                args.append(tree.NormalLambdaParameter(name, self.parse_test(allow_walrus=False)))
                self.parse_varargs_default_rest(args)
            else:
                args.append(tree.NormalLambdaParameter(name))
                while self.accept(','):
                    if self.would_accept((':', '{', ')')):
                        break
                    if self.accept('*'):
                        self.parse_varargs_star_rest(args)
                        break
                    if self.accept('**'):
                        self.parse_varargs_starstar_rest(args)
                        break
                    name = self.parse_name()
                    self.declvars(name)
                    if self.accept('='):
                        args.append(tree.NormalLambdaParameter(name, self.parse_test(allow_walrus=False)))
                        self.parse_varargs_default_rest(args)
                        break
                    args.append(tree.NormalLambdaParameter(name))
        return args

    def parse_varargs_starstar_rest(self, args: List[tree.LambdaParameter]):
        name = self.parse_name()
        args.append(tree.StarStarLambdaParameter(name))
        self.declvars(name)
        self.accept(',')

    def parse_varargs_star_rest(self, args: List[tree.LambdaParameter]):
        if self.token.exact_type == NAME:
            name = self.parse_name()
            args.append(tree.StarLambdaParameter(name))
            self.declvars(name)
        else:
            args.append(tree.StarLambdaParameter(None))
        while self.accept(','):
            if self.would_accept((':', '{', ')')):
                break
            if self.accept('**'):
                self.parse_varargs_starstar_rest(args)
                break
            name = self.parse_name()
            self.declvars(name)
            default = self.parse_if_accept(self.parse_test(), '=')
            args.append(tree.NormalLambdaParameter(name, default))

    def parse_varargs_default_rest(self, args: List[tree.LambdaParameter]):
        while self.accept(','):
            if self.would_accept((':', '{', ')')):
                break
            if self.accept('*'):
                self.parse_varargs_star_rest(args)
                break
            if self.accept('**'):
                self.parse_varargs_starstar_rest(args)
                break
            name = self.parse_name()
            self.declvars(name)
            self.require('=')
            default = self.parse_test(allow_walrus=False)
            args.append(tree.NormalLambdaParameter(name, default))

    def declvars(self, arg):
        if isinstance(arg, (str, tree.Name)):
            self.scope.definevar(arg)
        elif isinstance(arg, tree.VarExpression):
            self.scope.definevar(arg.name)
        elif isinstance(arg, tree.CommaExpression):
            for item in arg.items:
                self.declvars(item)
        elif isinstance(arg, tree.UnaryExpression) \
            and arg.operator in (tree.UnaryOperator.STAR, tree.UnaryOperator.STARSTAR) \
            and isinstance(arg.expr, tree.VarExpression):
            self.scope.definevar(arg.expr.name)
        elif not isinstance(arg, (tree.IndexExpression, tree.AttrExpression)):
            raise SyntaxError(f"not a valid lvalue: {arg}", self.position)

    def delvars(self, arg):
        if isinstance(arg, (str, tree.Name)):
            self.scope.delvar(arg)
        elif isinstance(arg, tree.VarExpression):
            self.scope.delvar(arg.name)
        elif isinstance(arg, tree.CommaExpression):
            for item in arg.items:
                self.delvars(item)
        elif isinstance(arg, tree.UnaryExpression) \
            and arg.operator in (tree.UnaryOperator.STAR, tree.UnaryOperator.STARSTAR) \
            and isinstance(arg.expr, tree.VarExpression):
            self.scope.delvar(arg.expr.name)
        elif not isinstance(arg, (tree.IndexExpression, tree.AttrExpression)):
            raise SyntaxError(f"not a valid lvalue: {arg}", self.position)

def parse_file(file) -> List[tree.Statement]:
    return PyJavaParser(tokenize(file.readline), getattr(file, 'name', '<unknown source>')).parse_file()

def parse_str(s: str, encoding='utf-8') -> List[tree.Statement]:
    import io
    return PyJavaParser(tokenize(io.BytesIO(bytes(s, encoding)).readline), '<string>').parse_file()


class MultiContextManager:
    def __init__(self, *mgrs):
        if len(mgrs) == 0:
            raise ValueError("no context managers given")
        for mgr in mgrs:
            if not hasattr(mgr, '__enter__') or not hasattr(mgr, '__exit__') \
                or not callable(mgr.__enter__) or not callable(mgr.__exit__):
                raise ValueError(f"{type(mgr).__name__} is not a valid context manager")
        self._mgrs = mgrs

    def __enter__(self):
        for mgr in self._mgrs:
            mgr.__enter__()

    def __exit__(self, exc_typ, exc_val, exc_tb):
        for mgr in self._mgrs:
            mgr.__exit__(exc_typ, exc_val, exc_tb)

from enum import auto
class ExpressionContext(Enum):
    NORMAL = auto()
    COMPREHENSION = auto()
    CLASS = auto()
del auto

class ExpressionContextManager:
    def __init__(self):
        self._contexts: List[ExpressionContext] = [ExpressionContext.NORMAL]

    def __enter__(self):
        pass

    def __exit__(self, exc_typ, exc_err, exc_tb):
        if len(self._contexts) == 1:
            raise RuntimeError("cannot exit base context")
        self._contexts.pop()

class BodyStatementContextManager:
    def __init__(self):
        self._stack: List[Tuple[List[tree.Statement], List[Tuple[int, Callable[[], Union[tree.Statement, List[tree.Statement], Tuple[Union[tree.Statement, List[tree.Statement]], Union[tree.Statement, List[tree.Statement]]]]]]]]] = []

    def __enter__(self):
        pass

    def __exit__(self, exc_typ, exc_val, exc_tb):
        if not (exc_typ or exc_val or exc_tb):
            stmts, to_do = self._stack.pop()
            if to_do:
                add = 0
                idx = 0
                def insert(val):
                    nonlocal add, idx
                    add1 = 0
                    if isinstance(val, list):
                        for item in val:
                            add1 += insert(item)
                    elif isinstance(val, tuple):
                        if len(val) != 2:
                            raise ValueError
                        
                        if isinstance(val[1], list):
                            for item in val[1]:
                                if not isinstance(item, tree.Statement):
                                    raise TypeError
                                add1 += 1
                                stmts.insert(idx+add+add1, item)
                        elif isinstance(val[1], tree.Statement):
                            add1 += 1
                            stmts.insert(idx+add+add1, val[1])
                        else:
                            raise TypeError
                        
                        if isinstance(val[0], list):
                            for item in val[0]:
                                if not isinstance(item, tree.Statement):
                                    raise TypeError
                                stmts.insert(idx+add, item)
                                add += 1
                        elif isinstance(val[0], tree.Statement):
                            stmts.insert(idx+add, val[0])
                            add += 1
                        else:
                            raise TypeError

                        add += add1
                    elif isinstance(val, tree.Statement):
                        stmts.insert(idx+add, val)
                        add += 1
                    else:
                        raise TypeError

                for idx, func in to_do:
                    insert(func())

class ParserScope:
    def __init__(self, parser: PyJavaParser):
        import builtins
        self._scopes: List[Set[str]] = [set(dir(builtins))]
        self.parser = parser

    def __enter__(self):
        self._scopes.append(set())

    def __exit__(self, exc_typ, exc_err, exc_tb):
        if len(self._scopes) == 1:
            raise RuntimeError("cannot exit global scope")
        self._scopes.pop()

    def __contains__(self, varname: Union[str, tree.Name]):
        if isinstance(varname, tree.Name):
            varname = str(varname)
        elif not isinstance(varname, str):
            return False
        for scope in reversed(self._scopes):
            if varname in scope:
                return True
        return False

    @property
    def current(self) -> Set[str]:
        return self._scopes[-1]

    def definevar(self, varname: Union[str, tree.Name]):
        if isinstance(varname, tree.Name):
            varname = str(varname)
        elif not isinstance(varname, str):
            raise TypeError("expected varname to be str or Name")
        if varname.startswith('__lambda'):
            raise SyntaxError("all names starting with __lambda are reserved", self.parser.position)
        self.current.add(varname)

    def delvar(self, varname: Union[str, tree.Name]):
        if isinstance(varname, tree.Name):
            varname = str(varname)
        elif not isinstance(varname, str):
            raise TypeError("expected varname to be str or Name")
        for scope in reversed(self._scopes):
            if varname in scope:
                scope.remove(varname)
                return True
        return False

    def __str__(self):
        return str(list(reversed(self._scopes)))