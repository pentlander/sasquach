grammar Sasquach;

// parser rules
compilationUnit : moduleDeclaration EOF;
moduleDeclaration : moduleName '{' moduleBody '}' ;

moduleBody: function* ;
moduleName: ID;

block : '{' (blockStatement)* (returnExpression=expression)? '}' ;
function : functionDeclaration block ;
functionDeclaration locals [ int paramIndex ] : FUNCTION functionName '(' (functionArgument[ $paramIndex++ ])? (',' functionArgument[ $paramIndex++ ])* '):' type ;
functionName : ID ;
functionArgument [ int index ] : ID ':' type ;

type : primitiveType | structType ;
primitiveType : 'boolean' | 'string' ('[' ']')* | 'char' | 'byte' | 'int' | 'long' | 'float' | 'double' | 'void' ;
structType : QUALIFIED_NAME ;

blockStatement locals [ int lastVarIndex ]: variableDeclaration[ $lastVarIndex++ ] | printStatement | functionCall ;

variableDeclaration [ int index ] : VARIABLE identifier EQUALS expression ;
identifier : ID ;
printStatement : PRINT expression ;
ifBlock : IF ifCondition=expression trueBlock=block (ELSE falseBlock=block)? ;
functionCall : functionName LP expressionList RP ;

expressionList : (expression)? (',' expression)* ;
expression : left=expression operator=(DIVISION|ASTERISK) right=expression #binaryOperation
| left=expression operator=(PLUS|MINUS) right=expression #binaryOperation
| LP expression RP #parenExpression
| left=expression operator=(EQ|NEQ|GE|GT|LE|LT) right=expression #compareExpression
| value #valueLiteral
| identifier  #identifierExpression
| functionCall #functionExpression
| ifBlock #ifExpression;

value : NUMBER #intLiteral
      | STRING #stringLiteral
      | TRUE #booleanLiteral
      | FALSE #booleanLiteral ;

// Lexer Rules (tokens)

// Keywords
FUNCTION : 'fn' ;
IF       : 'if' ;
ELSE     : 'else' ;
VARIABLE : 'let' ;
PRINT    : 'print' ;

// Literals
NUMBER : '0'|[1-9][0-9]* ;
STRING : '"'.*?'"' ;
TRUE   : 'true';
FALSE  : 'false';

// Operators
PLUS     : '+' ;
MINUS    : '-' ;
ASTERISK : '*' ;
DIVISION : '/' ;
EQUALS   : '=' ;
GT       : '>' ;
LT       : '<' ;
GE       : '>=' ;
LE       : '<=' ;
EQ       : '==' ;
NEQ      : '!=' ;
LP       : '(' ;
RP       : ')' ;

// Identifiers
ID : [a-zA-Z][a-zA-Z0-9]+ ;
QUALIFIED_NAME : ID ('.' ID)+;
WS : [ \t\n\r]+ -> skip ;