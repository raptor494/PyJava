parser grammar PyJavaParser;

options {
  tokenVocab=PyJavaLexer;
  superClass=PyJavaParserBase;
}

@header {
import java.util.List;
import java.util.ArrayList;

import pyjava.PyJavaOptions;
}

@members {
    public PyJavaParser(TokenStream input, PyJavaOptions optionsIn) {
        this(input);
        options = optionsIn;
    }
}

file
  : statement* comments EOF
  ;


statement
returns [List<Token> commentTokens]
@init {
    $commentTokens = getPrecedingLineComments();
}
  : ';' comment                            # EmptyStatement
  | assignment eos                                                   # AssignmentStatement
  | starExpressions eos                                              # ExpressionStatement
  | 'return' retVal? eos                                             # ReturnStatement
  | yieldExpression eos                                              # YieldStatement
  | {!options.forceParensInReturnYieldRaise()}?
    'raise' {notLineTerminator()}? expression {notLineTerminator()}? 'from' expression eos                   # RaiseFromStatement
  | {options.forceParensInReturnYieldRaise()}?
    'raise' {notLineTerminator()}? namedExpressionCond {notLineTerminator()}? 'from' namedExpressionCond eos # RaiseFromStatement
  | 'raise' retVal? eos                                               # RaiseStatement
  | 'import' dottedAsNames eos                                        # ImportStatement
  | 'from' importFromName 'import' importFromTargets eos              # FromImportStatement
  | 'pass' eos                                                        # EmptyStatement
  | 'del' delTargets eos                                              # DelStatement
  | {!options.forceParensInReturnYieldRaise()}?
    'assert' expression (',' expression)? eos                         # AssertStatement
  | {options.forceParensInReturnYieldRaise()}?
    'assert' '(' namedExpression (',' namedExpression)? ','? ')' eos  # AssertStatement
  | 'break' eos                                                       # BreakStatement
  | 'continue' eos                                                    # ContinueStatement
  | 'global' identifier (',' identifier)* eos                         # GlobalStatement
  | 'nonlocal' identifier (',' identifier)* eos                       # NonLocalStatement
  | decorators? funcHeader retType? comment comments STRING_LITERAL? funcBody          # FunctionDef
  | decorators? classHeader comment comments STRING_LITERAL? classBody                 # ClassDef
  | 'if' namedExpressionCond block elif* elseBlock?                   # IfStatement
  | 'while' namedExpressionCond block elseBlock?                      # WhileLoop
  | 'async'? 'for' forLoopHeader block elseBlock?                     # ForLoop
  | 'async'? 'with' withItems block                                   # WithStatement
  | 'try' block finallyBlock                                          # TryFinallyStatement
  | 'try' block exceptBlock+ elseBlock? finallyBlock?                 # TryExceptStatement
  | 'match' {notLineTerminator()}? subjectExprCond comment '{' ({lineTerminatorAhead()}? comment)? caseBlock+ '}' # MatchStatement
  ;

retVal
  : {options.forceParensInReturnYieldRaise()}? {notLineTerminator()}? '(' starExpressions? ')'
  | {!options.forceParensInReturnYieldRaise()}? {notLineTerminator()}? starExpressions
  ;

assignment
  : identifier annotation ('=' annotatedRhs)?                     # VarDeclAssignment
  | '(' singleTarget ')' annotation ('=' annotatedRhs)?           # AnnotatedAssignment
  | singleSubscriptAttributeTarget annotation ('=' annotatedRhs)? # AnnotatedAssignment
  | (starTargets '=')+ annotatedRhs                               # MultipleAssignment
  | singleTarget augAssign annotatedRhs                           # AugAssignment
  ;

augAssign
  : '+='
  | '-='
  | '*='
  | '@='
  | '/='
  | '%='
  | '//='
  | '&='
  | '^='
  | '|='
  | '<<='
  | '>>='
  | '**='
  ;

singleTarget
  : singleSubscriptAttributeTarget
  | identifier
  | '(' singleTarget ')'
  ;

singleSubscriptAttributeTarget
  : tPrimary '.' identifier
  | tPrimary {notLineTerminator()}? '[' slices ']'
  ;

block
  : comment '{' ({lineTerminatorAhead()}? comment)? statement* comments '}'
  | {options.allowColonSimpleBlocks()}? ':' ({lineTerminatorAhead()}? comment)? {!next(SEMI)}? statement
  | {options.allowNoColonSimpleBlocks()}? ({lineTerminatorAhead()}? comment)? statement
  ;

elif
returns [List<Token> commentTokens]
@init {
    $commentTokens = getPrecedingLineComments();
}
  : 'elif' namedExpressionCond block
  ;

elseBlock
returns [List<Token> commentTokens]
@init {
    $commentTokens = getPrecedingLineComments();
}
  : 'else' block
  ;

forLoopHeader
  : '(' starTargets 'in' starExpressions ')'
  | {!options.forceParensInStatements()}? starTargets 'in' starExpressions
  ;

withItems
  : '(' withItem (',' withItem)* ','? ')'
  | {!options.forceParensInStatements()}? withItem (',' withItem)*
  ;

withItem
  : expression ('as' starTarget)?
  ;

exceptBlock
  : 'except' (exceptItem | '(' ')')? block
  ;

exceptItem
  : '(' expression ('as' identifier)? ')'
  | {!options.forceParensInStatements()}? expression ('as' identifier)?
  ;

finallyBlock
  : 'finally' block
  ;

subjectExprCond
  : {options.forceParensInStatements()}? namedExpressionCond
  | {!options.forceParensInStatements()}? subjectExpr
  ;

subjectExpr
  : starNamedExpression ',' starNamedExpressions?
  | namedExpression
  ;

caseBlock
returns [List<Token> commentTokens]
@init {
    $commentTokens = getPrecedingLineComments();
}
  : {options.forceParensInReturnYieldRaise()}? 'case' '(' patterns? ')' guard? block
  | {!options.forceParensInReturnYieldRaise()}? 'case' patterns guard? block
  ;

guard
  : 'if' namedExpressionCond
  ;


importFromName
  : dots? dottedName
  | dots
  ;

dots
  : ('.' | '...')+
  ;

importFromTargets
  : '(' importFromAsNames ','? ')'
  | importFromAsNames
  | '*'
  ;

importFromAsNames
  : importFromAsName (',' importFromAsName)*
  ;

importFromAsName
  : name=identifier ('as' alias=identifier)?
  ;

dottedAsNames
  : dottedAsName (',' dottedAsName)*
  ;

dottedAsName
  : name=dottedName ('as' alias=identifier)?
  ;

dottedName
  : identifier ('.' identifier)*
  ;


delTargets
  : {!options.forceParensInReturnYieldRaise()}? delTarget (',' delTarget)*
  | '(' (delTarget (',' delTarget)* ','?)? ')'
  ;

delTarget
  : tPrimary '.' identifier                            # PropertyDelTarget
  | tPrimary {notLineTerminator()}? '[' slices ']'                            # SliceDelTarget
  | identifier                                         # NameDelTarget
  | '(' delTarget ')'                                  # DelTargetParens
  | '(' (delTarget ((',' delTarget)+ ','? | ','))? ')' # DelTargetList
  | '[' (delTarget (',' delTarget)* ','?)? ']'         # DelTargetList
  ;


tPrimary
  : tPrimary '.' identifier                            # PropertyTPrimary
  | tPrimary {notLineTerminator()}? '[' slices ']'     # SliceTPrimary
  | tPrimary {notLineTerminator()}? genExp             # CallWithGenExpTPrimary
  | tPrimary {notLineTerminator()}? '(' arguments? ')' # CallTPrimary
  | atom                                               # AtomTPrimary
  ;


classHeader
  : 'class' identifier ('(' arguments? ')')?
  ;


funcHeader
  : 'async'? 'def' identifier '(' parameters? ')'
  ;

retType
  : '->' expression
  ;

funcBody
  : '{' statement* comments '}'
  ;

classBody
  : '{' statement* comments '}'
  ;

parameters
  : slashNoDefault (',' paramsNoDefault)? (',' paramsWithDefault)? (
    ',' starEtc
  )? ','?
  | slashWithDefault (',' paramsWithDefault) (',' starEtc)? ','?
  | paramsNoDefault (',' paramsWithDefault)? (',' starEtc)? ','?
  | paramsWithDefault (',' starEtc)? ','?
  | starEtc ','?
  ;

slashNoDefault
  : paramsNoDefault ',' '/'
  ;

slashWithDefault
  : paramNoDefault (',' paramNoDefault)* (',' paramWithDefault)+ ',' '/'
  | paramWithDefault (',' paramWithDefault)* ',' '/'
  ;

paramsNoDefault
  : paramNoDefault (',' paramNoDefault)*
  ;

paramNoDefault
  : identifier annotation?
  ;

paramsWithDefault
  : paramWithDefault (',' paramWithDefault)*
  ;

paramWithDefault
  : identifier annotation? defaultVal
  ;

paramMaybeDefault
  : identifier annotation? defaultVal?
  ;

starEtc
  : '*' paramNoDefault (',' paramMaybeDefault)* (',' kwds)?
  | '*' (',' paramMaybeDefault)+ (',' kwds)?
  | kwds
  ;

kwds
  : '**' paramNoDefault
  ;


decorators
@init {
    boolean temp_inDecorator = inDecorator;
    inDecorator = true;
}
  : decorator+
  ;
finally {
    inDecorator = temp_inDecorator;
}

decorator
  : '@' namedExpression comment comments
  ;


patterns
  : openSequencePattern
  | pattern
  ;

pattern
  : asPattern
  | orPattern
  ;

asPattern
  : orPattern 'as' patternCaptureTarget
  ;

orPattern
  : closedPattern ('|' closedPattern)*
  ;

closedPattern
  : literalExpr                                                    # LiteralPattern
  | patternCaptureTarget                                           # CapturePattern
  | {next("_")}? NAME                                              # WildcardPattern
  | attr                                                           # ValuePattern
  | '(' pattern ')'                                                # GroupPattern
  | '[' maybeSequencePattern? ']'                                  # ListSequencePattern
  | '(' openSequencePattern? ')'                                   # TupleSequencePattern
  | '{' '}'                                                        # MappingPattern
  | '{' doubleStarPattern ','? '}'                                 # MappingPattern
  | '{' itemsPattern ',' doubleStarPattern ','? '}'                # MappingPattern
  | '{' itemsPattern ','? '}'                                      # MappingPattern
  | nameOrAttr '(' ')'                                             # ClassPattern
  | nameOrAttr '(' positionalPatterns ','? ')'                     # ClassPattern
  | nameOrAttr '(' keywordPatterns ','? ')'                        # ClassPattern
  | nameOrAttr '(' positionalPatterns ',' keywordPatterns ','? ')' # ClassPattern
  ;

nameOrAttr
  : attr
  | identifier
  ;

complexNumber
  : signedRealNumber sign imaginaryNumber
  ;

sign
  : '+'
  | '-'
  ;

signedNumber
  : '-'? NUMBER
  ;

signedRealNumber
  : '-'? realNumber
  ;

realNumber
  : {nextIsRealNumber()}? NUMBER
  ;

imaginaryNumber
  : {nextIsImagNumber()}? NUMBER
  ;

attr
  : dottedName '.' identifier
  ;

openSequencePattern
  : maybeStarPattern ',' maybeSequencePattern?
  ;

maybeSequencePattern
  : maybeStarPattern (',' maybeStarPattern)* ','?
  ;

maybeStarPattern
  : starPattern
  | pattern
  ;

starPattern
  : '*' {next("_")}? NAME
  | '*' patternCaptureTarget
  ;

itemsPattern
  : keyValuePattern (',' keyValuePattern)*
  ;

keyValuePattern
  : literalExpr ':' pattern
  | attr ':' pattern
  ;

doubleStarPattern
  : '**' patternCaptureTarget
  ;

literalExpr
  : complexNumber # ComplexLiteralPattern
  | signedNumber  # NumberLiteralPattern
  | strings       # StringLiteralPattern
  | 'None'        # NoneLiteralPattern
  | 'True'        # TrueLiteralPattern
  | 'False'       # FalseLiteralPattern
  ;

patternCaptureTarget
  : {!next("_")}? identifier {!(next(EQ) || next(DOT) || next(LPAREN))}?
  ;

positionalPatterns
  : pattern (',' pattern)*
  ;

keywordPatterns
  : keywordPattern (',' keywordPattern)*
  ;

keywordPattern
  : identifier '=' pattern
  ;


starExpressions
  : starExpression (',' starExpression)* ','?
  ;

starExpression
  : '*' bitwiseOr
  | expression
  ;

starNamedExpressions
  : starNamedExpression (',' starNamedExpression)* ','?
  ;

starNamedExpression
  : '*' bitwiseOr
  | namedExpression
  ;


assignmentExpression
  : identifier ':=' expression
  ;

namedExpressionCond
  : {options.forceParensInStatements()}? 'not'? 
    {next(LPAREN)}? atom
  | {!options.forceParensInStatements()}? namedExpression
  ;

namedExpression
  : assignmentExpression
  | expression {!next(COLONEQ)}?
  ;

annotatedRhs
  : yieldExpression
  | starExpressions
  ;

expressions
  : expression (',' expression)* ','?
  ;

expression
  : disjunction 'if'
    {
        boolean temp_inDecorator = inDecorator;
        inDecorator = false;
    }
    disjunction 
    {
        inDecorator = temp_inDecorator;
    }
    'else' expression           # IfExpression
  | disjunction                 # DisjunctionExpression
  | lambdaHeader ':' expression # LambdaExpression
  ;

lambdaHeader
@init {
    boolean temp_inDecorator = inDecorator;
    inDecorator = false;
}
  : 'async'? 'lambda' (lambdaParameters | '(' lambdaParameters ')' | '(' parameters? ')')? retType?
  ;
finally {
    inDecorator = temp_inDecorator;
}

lambdaParameters
  : lambdaSlashNoDefault (',' lambdaParamsNoDefault)? (
    ',' lambdaParamsWithDefault
  )? (',' lambdaStarEtc)? ','?
  | lambdaSlashWithDefault (',' lambdaParamsWithDefault) (
    ',' lambdaStarEtc
  )? ','?
  | lambdaParamsNoDefault (',' lambdaParamsWithDefault)? (
    ',' lambdaStarEtc
  )? ','?
  | lambdaParamsWithDefault (',' lambdaStarEtc)? ','?
  | lambdaStarEtc
  ;

lambdaSlashNoDefault
  : lambdaParamsNoDefault ',' '/'
  ;

lambdaSlashWithDefault
  : lambdaParamNoDefault (',' lambdaParamNoDefault)* (
    ',' lambdaParamWithDefault
  )+
  | lambdaParamWithDefault (',' lambdaParamWithDefault)*
  ;

lambdaParamsNoDefault
  : lambdaParamNoDefault (',' lambdaParamNoDefault)*
  ;

lambdaParamNoDefault
  : identifier
  ;

lambdaParamsWithDefault
  : lambdaParamWithDefault (',' lambdaParamWithDefault)*
  ;

lambdaParamWithDefault
  : identifier defaultVal
  ;

lambdaParamMaybeDefault
  : identifier defaultVal?
  ;

lambdaStarEtc
  : '*' lambdaParamNoDefault (',' lambdaParamMaybeDefault)* (
    ',' lambdaKwds
  )?
  | '*' (',' lambdaParamMaybeDefault)+ (',' lambdaKwds)?
  | lambdaKwds
  ;

lambdaKwds
  : '**' lambdaParamNoDefault
  ;


disjunction
  : conjunction ('or' conjunction)*
  ;

conjunction
  : inversion ('and' inversion)*
  ;

inversion
  : 'not' inversion
  | comparison
  ;

comparison
  : bitwiseOr compareOpBitwiseOrPair+
  | bitwiseOr
  ;

compareOpBitwiseOrPair
  : compareOp bitwiseOr
  ;

compareOp
  : '=='
  | '!='
  | '<='
  | '<'
  | '>='
  | '>'
  | 'not' 'in'
  | 'in'
  | 'is' 'not'
  | 'is'
  ;

bitwiseOr
  : bitwiseOr '|' bitwiseXor
  | bitwiseXor
  ;

bitwiseXor
  : bitwiseXor '^' bitwiseAnd
  | bitwiseAnd
  ;

bitwiseAnd
  : bitwiseAnd '&' shiftExpr
  | shiftExpr
  ;

shiftExpr
  : shiftExpr shiftOp sum
  | sum
  ;

shiftOp
  : '<<'
  | '>>'
  ;

sum
  : sum sumOp term
  | term
  ;

sumOp
  : '+'
  | '-'
  ;

term
  : term termOp factor
  | factor
  ;

termOp
  : '*'
  | '/'
  | '//'
  | '%'
  | {!inDecorator}? '@'
  ;

factor
  : prefixOp factor
  | power
  ;

prefixOp
  : '+'
  | '-'
  | '~'
  ;

power
  : awaitPrimary '**' factor
  | awaitPrimary
  ;

awaitPrimary
  : 'await' primary
  | primary
  ;

primary
  : primary '.' identifier                            # PropertyPrimary
  | primary {notLineTerminator()}? genExp             # CallWithGenExpPrimary
  | primary {notLineTerminator()}? '(' arguments? ')' # CallPrimary
  | primary {notLineTerminator()}? '[' slices ']'                            # SlicePrimary
  | atom                                              # AtomPrimary
  ;

slices
@init {
    boolean temp_inDecorator = inDecorator;
    inDecorator = false;
}
  : slice (',' slice)* ','?
  ;
finally {
    inDecorator = temp_inDecorator;
}

slice
  : begin=expression? ':' end=expression? (
    ':' step=expression?
  )?
  | namedExpression
  ;

atom
@init {
    boolean temp_inDecorator = inDecorator;
    inDecorator = false;
}
  : identifier                                                  # NamedAtom
  | 'True'                                                      # TrueAtom
  | 'False'                                                     # FalseAtom
  | 'None'                                                      # NoneAtom
  | strings                                                     # StringsAtom
  | NUMBER                                                      # NumberAtom
  | '(' yieldExpression ')'                                     # GroupAtom
  | '(' namedExpression ')'                                     # GroupAtom
  | '(' (starNamedExpressions {((TupleAtomContext)$ctx).starNamedExpressions().COMMA(0) != null}?)? ')' # TupleAtom
  | genExp                                                      # GenExpAtom
  | '[' starNamedExpressions? ']'                               # ListAtom
  | '[' namedExpression forIfClauses ']'                        # ListCompAtom
  | '{' doubleStarredKVPairs? '}'                               # DictAtom
  | '{' kVPair forIfClauses '}'                                 # DictCompAtom
  | '...'                                                       # EllipsisAtom
  | 'class' ('(' superClassArgs=arguments? ')')? (genExp | '(' constructorArgs=arguments? ')') classBody # AnonymousClassExpression
  | lambdaHeader funcBody                                       # MultiLineLambdaExpression
  ;
finally {
    inDecorator = temp_inDecorator;
}

doubleStarredKVPairs
  : doubleStarredKVPair (',' doubleStarredKVPair)* ','?
  ;

doubleStarredKVPair
  : '**' bitwiseOr
  | kVPair
  ;

kVPair
  : key=expression ':' value=expression
  ;

forIfClauses
  : forIfClause+
  ;

forIfClause
  : 'async'? 'for' starTargets 'in' disjunction filter*
  ;

filter
  : 'if' disjunction
  ;

genExp
@init {
    boolean temp_inDecorator = inDecorator;
    inDecorator = false;
}
  : '(' assignmentExpression forIfClauses ')'
  | '(' expression forIfClauses ')'
  ;
finally {
    inDecorator = temp_inDecorator;
}

yieldExpression
  : 'yield' {notLineTerminator()}? 'from' 
    ( {!options.forceParensInReturnYieldRaise()}? 
      expression
    | {options.forceParensInReturnYieldRaise()}?
      {next(LPAREN)}? atom
    )
  | 'yield' retVal?
  ;


arguments
@init {
    boolean temp_inDecorator = inDecorator;
    inDecorator = false;
}
  : argument (',' argument)* (',' kwargs)? ','?
  | kwargs ','?
  ;
finally {
    inDecorator = temp_inDecorator;
}

argument
  : starredExpression
  | assignmentExpression
  | expression
  ;

starredExpression
  : '*' expression
  ;

kwargs
  : kwargOrStarred (',' kwargOrStarred)* (
    ',' kwargOrDoubleStarred
  )*
  | kwargOrDoubleStarred (',' kwargOrDoubleStarred)*
  ;

kwargOrStarred
  : identifier '=' expression
  | starredExpression
  ;
  

kwargOrDoubleStarred
  : identifier '=' expression
  | '**' expression
  ;


starTargets
  : starTarget (',' starTarget)* ','?
  ;

starTarget
  : '*' targetWithStarAtom
  | targetWithStarAtom
  ;

targetWithStarAtom
  : tPrimary '.' identifier                        # PropertyTargetWithStarAtom
  | tPrimary {notLineTerminator()}? '[' slices ']' # SliceTargetWithStarAtom
  | starAtom                                       # TargetStarAtom
  ;

starAtom
  : identifier                                # NamedStarAtom
  | '(' targetWithStarAtom ')'                # StarAtomGroup
  | '(' (starTargets {((TupleStarAtomContext)$ctx).starTargets().COMMA(0) != null}?)? ')' # TupleStarAtom
  | '[' starTargets? ']'                      # ListStarAtom
  ;


strings
  : (STRING_LITERAL | BYTES_LITERAL)+
  ;


annotation
  : ':' expression
  ;

defaultVal
  : '=' expression
  ;

identifier
  : NAME
  | 'match'
  | 'case'
  ;

comment
returns [Token commentToken]
  : {$commentToken = getFirstPrecedingComment();}
  ;

comments
returns [List<Token> commentTokens]
  : {$commentTokens = getPrecedingLineComments();}
  ;

eos
  : ';' comment
  | {!options.requireSemicolons()}? 
    comment
    ( EOF
    | {lineTerminatorAhead()}?
    | {closeBrace()}?
    // | {options.forceParensInReturnYieldRaise()}?
    //| {if(true) throw new FailedPredicateException(this, "false", "expected semicolon or end of statement");}
    )
  | {options.requireSemicolons()}? {if(true) throw new FailedPredicateException(this, "false", "expected semicolon");} /*{false}?<fail='expected semicolon'>*/
  ;