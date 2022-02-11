grammar Sasquach;

// parser rules
compilationUnit : NL* moduleDeclaration+ EOF;
moduleDeclaration : moduleName struct NL* ;

moduleName: ID;

qualifiedName : ID ('/' ID)+;
use: USE FOREIGN? qualifiedName ;

block : '{' NL* blockStatement (NL blockStatement)*  NL* '}' ;
function : functionDeclaration expression ;
functionDeclaration :
    ('[' typeIdentifier (',' typeIdentifier)* ']')?
    functionParameterList ':' type '->' NL* ;
functionName : ID ;
functionArgument : ID ':' type ;
functionParameterList : '(' (functionArgument)? (',' functionArgument)* ')' ;

type : primitiveType | classType | structType | typeIdentifier | functionType | moduleNamedType ;
primitiveType : 'Boolean' | 'String' ('[' ']')* | 'Char' | 'Byte' | 'Int' | 'Long' | 'Float' | 'Double' | 'Void' ;
classType : qualifiedName ;
structType : '{' NL* ID ':' NL* type (',' NL* ID ':' NL* type)* NL* '}' ;
functionType : functionParameterList '->' type ;
moduleNamedType: moduleName NL* '.' typeIdentifier ;

blockStatement : variableDeclaration | printStatement | expression ;

variableDeclaration : VARIABLE ID EQUALS expression ;
typeIdentifier: ID ;
varReference : ID ;
memberName : ID ;
foreignName: ID ;
printStatement : PRINT expression ;
ifBlock : IF ifCondition=expression trueBlock=expression (ELSE falseBlock=expression)? ;
functionCall : functionName application ;

expressionList : expression (',' expression)* ;
application :  LP expressionList RP ;

expression :
    left=expression operator=(DIVISION|ASTERISK) right=expression #binaryOperation
  | left=expression operator=(PLUS|MINUS) right=expression #binaryOperation
  | LP expression RP #parenExpression
  | left=expression operator=(EQ|NEQ|GE|GT|LE|LT) right=expression #compareExpression
  | value #valueLiteral
  | struct #structLiteral
  | varReference  #varExpression
  | functionCall #functionExpression
  | ifBlock #ifExpression
  | foreignName '#' memberName (application)? #foreignMemberAccessExpression
  | expression '.' memberName (application)? #memberAccessExpression
  | block #blockExpression ;

struct : '{' NL* structStatement (',' NL* structStatement)* NL* '}' ;
structStatement : use #useStatement
  | TYPEALIAS typeIdentifier EQUALS type #typeAliasStatement
  | memberName EQUALS (expression|function) #identifierStatement ;

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
TYPEALIAS : 'type' ;

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
NL : '\n' | 'r' '\n'? ;
WS : [ \t]+ -> skip ;