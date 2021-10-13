lexer grammar PyJavaLexer;

channels { ERROR }

// tokens { FSTRING_START_EXPR, FSTRING_ATOM }

options {
  superClass=PyJavaLexerBase;
}

BLOCK_COMMENT
  : /* {!isInTemplateString()}? */ '#{' .*? '#}' -> channel(HIDDEN)
  ;

LINE_COMMENT
  : /* {!isInTemplateString()}? */ '#' (~[{\r\n\f] (~[\r\n\f])*)? -> channel(HIDDEN)
  ;

NUMBER
  : INTEGER
  | FLOAT_NUMBER
  | IMAG_NUMBER
  ;

fragment INTEGER
  : DECIMAL_INTEGER
  | OCT_INTEGER
  | HEX_INTEGER
  | BIN_INTEGER
  ;

DEF: 'def';
RETURN: 'return';
RAISE: 'raise';
FROM: 'from';
IMPORT: 'import';
AS: 'as';
GLOBAL: 'global';
NONLOCAL: 'nonlocal';
ASSERT: 'assert';
IF: 'if';
ELIF: 'elif';
ELSE: 'else';
WHILE: 'while';
FOR: 'for';
IN: 'in';
TRY: 'try';
FINALLY: 'finally';
WITH: 'with';
EXCEPT: 'except';
LAMBDA: 'lambda';
OR: 'or';
AND: 'and';
NOT: 'not';
IS: 'is';
NONE: 'None';
TRUE: 'True';
FALSE: 'False';
CLASS: 'class';
YIELD: 'yield';
DEL: 'del';
PASS: 'pass';
CONTINUE: 'continue';
BREAK: 'break';
ASYNC: 'async';
AWAIT: 'await';
MATCH: 'match';
CASE: 'case';

NAME
  : ID_START ID_CONTINUE*
  ;

STRING_LITERAL
  : ( [rR] | [uU] | FSTRING_BEGIN )? (SHORT_STRING | LONG_STRING)
  ;

// SINGLE_SHORT_FSTRING_QUOTE
//   : FSTRING_BEGIN '\'' {increaseTemplateDepth();} -> pushMode(SINGLE_SHORT_FSTRING_TEMPLATE)
//   ;

// DOUBLE_SHORT_FSTRING_QUOTE
//   : FSTRING_BEGIN '"'  {increaseTemplateDepth();} -> pushMode(DOUBLE_SHORT_FSTRING_TEMPLATE)
//   ;

// SINGLE_LONG_FSTRING_QUOTE
//   : FSTRING_BEGIN '\'\'\'' {increaseTemplateDepth();} -> pushMode(SINGLE_LONG_FSTRING_TEMPLATE)
//   ;

// DOUBLE_LONG_FSTRING_QUOTE
//   : FSTRING_BEGIN '"""'    {increaseTemplateDepth();} -> pushMode(DOUBLE_LONG_FSTRING_TEMPLATE)
//   ;
  
fragment FSTRING_BEGIN
  : [fF] | ( [fF] [rR] ) | ( [rR] [fF] )
  ;

BYTES_LITERAL
  : ( [bB] | ( [bB] [rR] ) | ( [rR] [bB] ) ) (SHORT_BYTES | LONG_BYTES)
  ;

fragment DECIMAL_INTEGER
  : NON_ZERO_DIGIT ('_'* DIGITS)?
  | '0'+ ('_'+ '0'+)*
  ;

fragment OCT_INTEGER
  : '0' [oO] OCT_DIGITS
  ;

fragment HEX_INTEGER
  : '0' [xX] HEX_DIGITS
  ;

fragment BIN_INTEGER
  : '0' [bB] BIN_DIGITS
  ;

fragment FLOAT_NUMBER
  : POINT_FLOAT
  | EXPONENT_FLOAT
  ;

fragment IMAG_NUMBER
  : (FLOAT_NUMBER | DIGITS) [jJ]
  ;

DOT: '.';
ELLIPSIS: '...';
STAR: '*';
LPAREN: '(' {enterBracket('(');};
RPAREN: ')' {exitBracket(')');};
COMMA: ',';
COLON: ':';
SEMI: ';';
STARSTAR: '**';
EQ: '=';
LBRACK: '[' {enterBracket('[');};
RBRACK: ']' {exitBracket(']');};
BAR: '|';
CARET: '^';
AMP: '&';
LTLT: '<<';
GTGT: '>>';
PLUS: '+';
MINUS: '-';
SLASH: '/';
PER: '%';
TILDE: '~';
SLASHSLASH: '//';
LBRACE: '{' {enterBracket('{');};
// TEMPLATE_RBRACE: {isInTemplateString()}? '}' -> popMode;
RBRACE: '}' {exitBracket('}');};
LT: '<';
GT: '>';
EQEQ: '==';
GTEQ: '>=';
LTEQ: '<=';
LTGT: '<>';
BANGEQ: '!=';
AT: '@';
ARROW: '->';
PLUSEQ: '+=';
MINUSEQ: '-=';
STAREQ: '*=';
SLASHEQ: '/=';
PEREQ: '%=';
ATEQ: '@=';
BAREQ: '|=';
CARETEQ: '^=';
AMPEQ: '&=';
LTLTEQ: '<<=';
GTGTEQ: '>>=';
STARSTAREQ: '**=';
SLASHSLASHEQ: '//=';
COLONEQ: ':=';

SPACES: [ \t\f]+ -> skip;
NEWLINE: '\r'? '\n' {
    if (inCurlyBracketsOrNone()) {
        setChannel(HIDDEN);
    } else {
        skip();
    }
};

UNKNOWN: .;

// mode SINGLE_SHORT_FSTRING_TEMPLATE;

// SINGLE_SHORT_QUOTE_INSIDE
//   : '\'' {decreaseTemplateDepth();} -> type(SINGLE_SHORT_FSTRING_QUOTE), popMode
//   ;

// SINGLE_SHORT_FSTRING_START_EXPR
//   : '{' -> type(FSTRING_START_EXPR), pushMode(DEFAULT_MODE)
//   ;

// SINGLE_SHORT_FSTRING_ATOM
//   : (~['{\r\n])+ -> type(FSTRING_ATOM)
//   ;

// mode DOUBLE_SHORT_FSTRING_TEMPLATE;

// DOUBLE_SHORT_QUOTE_INSIDE
//   : '"' {decreaseTemplateDepth();} -> type(DOUBLE_SHORT_FSTRING_QUOTE), popMode
//   ;

// DOUBLE_SHORT_FSTRING_START_EXPR
//   : '{' -> type(FSTRING_START_EXPR), pushMode(DEFAULT_MODE)
//   ;

// DOUBLE_SHORT_FSTRING_ATOM
//   : (~["{\r\n])+ -> type(FSTRING_ATOM)
//   ;

// mode SINGLE_LONG_FSTRING_TEMPLATE;

// SINGLE_LONG_QUOTE_INSIDE
//   : '\'\'\'' {decreaseTemplateDepth();} -> type(SINGLE_LONG_FSTRING_QUOTE), popMode
//   ;

// SINGLE_LONG_FSTRING_START_EXPR
//   : '{' -> type(FSTRING_START_EXPR), pushMode(DEFAULT_MODE)
//   ;

// SINGLE_LONG_FSTRING_ATOM
//   : ( '\'\'' ~['{]
//     | '\'' ~['{]
//     | ~['{])+ -> type(FSTRING_ATOM)
//   ;

// mode DOUBLE_LONG_FSTRING_TEMPLATE;

// DOUBLE_LONG_QUOTE_INSIDE
//   : '"""' {decreaseTemplateDepth();} -> type(DOUBLE_LONG_FSTRING_QUOTE), popMode
//   ;

// DOUBLE_LONG_FSTRING_START_EXPR
//   : '{' -> type(FSTRING_START_EXPR), pushMode(DEFAULT_MODE)
//   ;

// DOUBLE_LONG_FSTRING_ATOM
//   : ( '""' ~["{]
//     | '"' ~["{]
//     | ~["{])+ -> type(FSTRING_ATOM)
//   ;


// Fragment rules

fragment SHORT_STRING
  : '\'' (STRING_ESCAPE_SEQ | ~[\\\r\n\f'])* '\''
  | '"'  (STRING_ESCAPE_SEQ | ~[\\\r\n\f"])* '"'
  ;

fragment LONG_STRING
  : '\'\'\'' (/* {!isInTemplateString()}? */ STRING_ESCAPE_SEQ | ~'\\')*? '\'\'\''
  | '"""'    (/* {!isInTemplateString()}? */ STRING_ESCAPE_SEQ | ~'\\')*? '"""'
  ;

fragment STRING_ESCAPE_SEQ
  : '\\' .
  | '\\' NEWLINE
  ;

fragment SHORT_BYTES
  : '\'' (SHORT_BYTES_CHAR_NO_SINGLE_QUOTE | BYTES_ESCAPE_SEQ)* '\''
  | '"'  (SHORT_BYTES_CHAR_NO_DOUBLE_QUOTE | BYTES_ESCAPE_SEQ)* '"'
  ;

fragment LONG_BYTES
  : '\'\'\'' (LONG_BYTES_CHAR | BYTES_ESCAPE_SEQ)*? '\'\'\''
  | '"""'    (LONG_BYTES_CHAR | BYTES_ESCAPE_SEQ)*? '"""'
  ;

fragment SHORT_BYTES_CHAR_NO_SINGLE_QUOTE
  : [\u0000-\u0009]
  | [\u000B-\u000C]
  | [\u000E-\u0026]
  | [\u0028-\u005B]
  | [\u005D-\u007F]
  ;

fragment SHORT_BYTES_CHAR_NO_DOUBLE_QUOTE
  : [\u0000-\u0009]
  | [\u000B-\u000C]
  | [\u000E-\u0021]
  | [\u0023-\u005B]
  | [\u005D-\u007F]
  ;

fragment LONG_BYTES_CHAR
  : [\u0000-\u005B]
  | [\u005D-\u007F]
  ;

fragment BYTES_ESCAPE_SEQ
  : '\\' [\u0000-\u007F]
  ;

fragment NON_ZERO_DIGIT
  : [1-9]
  ;

fragment DIGIT
  : [0-9]
  ;

fragment OCT_DIGIT
  : [0-7]
  ;

fragment HEX_DIGIT
  : [a-fA-F0-9]
  ;

fragment BIN_DIGIT
  : [01]
  ;

fragment DIGITS
  : DIGIT+ ('_'+ DIGIT+)*
  ;

fragment OCT_DIGITS
  : OCT_DIGIT+ ('_'+ OCT_DIGIT+)*
  ;

fragment HEX_DIGITS
  : HEX_DIGIT+ ('_'+ HEX_DIGIT+)*
  ;

fragment BIN_DIGITS
  : BIN_DIGIT+ ('_'+ BIN_DIGIT+)*
  ;

fragment POINT_FLOAT
  : DIGITS '.' DIGITS?
  | '.' DIGITS
  ;

fragment EXPONENT_FLOAT
  : (DIGITS | POINT_FLOAT) EXPONENT
  ;

fragment EXPONENT
  : [eE] [+-]? DIGITS
  ;

fragment UNICODE_OIDS
  : '\u1885'..'\u1886'
  | '\u2118'
  | '\u212e'
  | '\u309b'..'\u309c'
  ;

fragment UNICODE_OIDC
  : '\u00b7'
  | '\u0387'
  | '\u1369'..'\u1371'
  | '\u19da'
  ;

fragment ID_START
  : '_'
  | [\p{L}]
  | [\p{Nl}]
  // | [\p{Other_ID_Start}]
  | UNICODE_OIDS
  ;

fragment ID_CONTINUE
  : ID_START
  | [\p{Mn}]
  | [\p{Mc}]
  | [\p{Nd}]
  | [\p{Pc}]
  // | [\p{Other_ID_Continue}]
  | UNICODE_OIDC
  ;