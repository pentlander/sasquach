grammar Sasquach;

// parser rules
compilationUnit : NL* moduleDeclaration+ EOF;
moduleDeclaration : moduleName struct NL* ;

moduleName: ID;

qualifiedName : ID ('/' ID)+;
use: USE FOREIGN? qualifiedName ;

block : '{' NL* blockStatement (NL blockStatement NL*)*  NL* '}' ;
function : functionDeclaration expression ;
typeParameterList : ('[' typeIdentifier (',' typeIdentifier)* ']') ;
functionDeclaration :
    typeParameterList?
    functionParameterList ':' type '->' NL* ;
functionName : ID ;
functionArgument : ID ':' type ;
functionParameterList : '(' (functionArgument)? (',' functionArgument)* ')' ;

type : primitiveType | classType | structType | localNamedType | functionType | moduleNamedType | tupleType ;
primitiveType : 'Boolean' | 'String' ('[' ']')* | 'Char' | 'Byte' | 'Int' | 'Long' | 'Float' | 'Double' | 'Void' ;
classType : qualifiedName ;
structType : '{' NL* ID ':' NL* type (',' NL* ID ':' NL* type)* NL* '}' ;
functionType : functionParameterList '->' type ;
typeArgumentList : ('[' type (',' type)* ']') ;
localNamedType: typeIdentifier typeArgumentList? ;
moduleNamedType: moduleName NL* '.' typeIdentifier typeArgumentList? ;
tupleType : '(' type ',' ')'  | '(' type (',' type)+ ')' ;

blockStatement : variableDeclaration | printStatement | expression ;

variableDeclaration : VARIABLE ID EQUALS expression ;
typeIdentifier: ID ;
varReference : ID ;
memberName : ID ;
foreignName: ID ;
printStatement : PRINT expression ;
ifBlock : IF ifCondition=expression trueBlock=expression (ELSE falseBlock=expression)? ;
functionCall : functionName application ;
loop : LOOP '(' variableDeclaration? (',' NL* variableDeclaration)* ')' '->' NL* expression ;

expressionList : expression (',' expression)* ;
application :  LP expressionList? RP
  | LP expressionList? {notifyErrorListeners("Missing closing ')'");} ;
memberApplication : memberName application ;
tuple : '(' expression ',' ')' | '(' expression (',' expression)+ ')' ;

expression :
  expression '.' memberApplication #memberApplicationExpression
  | expression '.' memberName  #memberAccessExpression
  | foreignName '#' memberApplication #foreignMemberApplicationExpression
  | foreignName '#' memberName #foreignMemberAccessExpression
  | LP expression RP #parenExpression
  | left=expression operator=(DIVISION|ASTERISK) right=expression #binaryOperation
  | left=expression operator=(PLUS|MINUS) right=expression #binaryOperation
  | left=expression operator=(EQ|NEQ|GE|GT|LE|LT) right=expression #compareExpression
  | left=expression operator=(AND|OR) right=expression #booleanExpression
  | value #valueLiteral
  | struct #structLiteral
  | functionCall #functionExpression
  | ifBlock #ifExpression
  | varReference #varExpression
  | block #blockExpression
  | loop #loopExpression
  | function #functionExpression
  | expr=expression NL* APPLY NL* (functionCall | memberExpression=expression '.' memberApplication | foreignName '#' memberApplication) #applyExpression
  | tuple #tupleExpression ;

struct : '{' NL* structStatement (',' NL* structStatement)* (',')? NL* '}' ;
structStatement : use #useStatement
  | TYPEALIAS typeIdentifier typeParameterList? EQUALS type #typeAliasStatement
  | memberName EQUALS (function|expression) #identifierStatement ;

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
LOOP : 'loop' ;

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
AND      : '&&' ;
OR       : '||' ;
APPLY    : '|>' ;

// Identifiers
ID : [_a-zA-Z][$a-zA-Z0-9]* ;
FOREIGN_ID : [_a-zA-Z][$a-zA-Z0-9]* ;
NL : '\n' | 'r' '\n'? ;
WS : [ \t]+ -> skip ;