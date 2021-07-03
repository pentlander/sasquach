grammar Sasquach;

// parser rules
compilationUnit : moduleDeclaration+ EOF;
moduleDeclaration : moduleName struct ;

moduleName: ID;

qualifiedName : ID ('/' ID)+;
use: USE FOREIGN? qualifiedName ;

block : '{' (blockStatement)*? (returnExpression=expression)? '}' ;
function : functionDeclaration expression ;
functionDeclaration locals [ int paramIndex ] : '(' (functionArgument[ $paramIndex++ ])?
    (',' functionArgument[ $paramIndex++ ])* '):' type '->' ;
functionName : ID ;
functionArgument [ int index ] : ID ':' type ;

type : primitiveType | classType | structType ;
primitiveType : 'boolean' | 'string' ('[' ']')* | 'char' | 'byte' | 'int' | 'long' | 'float' | 'double' | 'void' ;
classType : qualifiedName ;
structType : '{' ID ':' type (',' ID ':' type)* '}' ;

blockStatement locals [ int lastVarIndex ]: variableDeclaration[ $lastVarIndex++ ] | printStatement | expression ;

variableDeclaration [ int index ] : VARIABLE ID EQUALS expression ;
varReference : ID ;
fieldName : ID ;
printStatement : PRINT expression ;
ifBlock : IF ifCondition=expression trueBlock=expression (ELSE falseBlock=expression)? ;
functionCall : functionName LP expressionList RP ;

expressionList : (expression)? (',' expression)* ;
expression :
    left=expression operator=(DIVISION|ASTERISK) right=expression #binaryOperation
  | left=expression operator=(PLUS|MINUS) right=expression #binaryOperation
  | LP expression RP #parenExpression
  | left=expression operator=(EQ|NEQ|GE|GT|LE|LT) right=expression #compareExpression
  | varReference '.' functionCall #functionAccess
  | left=expression '.' right=fieldName #fieldAccess
  | value #valueLiteral
  | struct #structLiteral
  | varReference  #varExpression
  | functionCall #functionExpression
  | ifBlock #ifExpression
  | block #blockExpression ;

struct : '{' (structStatement) (',' structStatement)* '}' ;
structStatement : use #useStatement
  | fieldName EQUALS (expression|function) #identifierStatement ;

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
FOREIGN : 'foreign' ;
USE : 'use' ;

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
ID : [a-zA-Z][a-zA-Z0-9]* ;
WS : [ \t\n\r]+ -> skip ;