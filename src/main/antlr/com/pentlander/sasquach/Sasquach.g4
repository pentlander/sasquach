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
structTypeField : NL* ID ':' NL* type | SPREAD ID? ;
structType : '{' structTypeField (',' structTypeField)* ','? NL* '}' ;
functionType : functionParameterList '->' type ;
typeArgumentList : ('[' type (',' type)* ']') ;
localNamedType: typeIdentifier typeArgumentList? ;
moduleNamedType: moduleName NL* '.' typeIdentifier typeArgumentList? ;
tupleType : '(' type ',' ')'  | '(' type (',' type)+ ')' ;
sumType : ('|' typeIdentifier variantType )+ ;
variantType :
    () #singletonType
  | '(' type ')' #singleTupleType
  | '(' type (',' NL* type)+ ')' #multiTupleType
  | structType #structSumType;

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

pattern :
    typeIdentifier #singletonPattern
  | typeIdentifier '(' ID ')' #singleTupleVariantPattern
  | typeIdentifier '(' ID (',' NL* ID)+ ')' #multiTupleVariantPattern
  | typeIdentifier '{' NL* ID (',' NL* ID)* (',')? NL* '}' #structVariantPattern ;
branch : pattern '->' expression ;
match : MATCH expression '{' NL* (branch ',' NL*)+ '}' ;

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
  | NOT expression #notExpression
  | left=expression operator=(DIVISION|ASTERISK) right=expression #binaryOperation
  | left=expression operator=(PLUS|MINUS) right=expression #binaryOperation
  | left=expression operator=(EQ|NEQ|GE|GT|LE|LT) right=expression #compareExpression
  | left=expression operator=(AND|OR) right=expression #booleanExpression
  | value #valueLiteral
  | struct #structLiteral
  | namedStruct #namedStructLiteral
  | functionCall #functionExpression
  | ifBlock #ifExpression
  | varReference #varExpression
  | block #blockExpression
  | loop #loopExpression
  | function #functionExpression
  | expr=expression NL* APPLY NL* (functionCall | memberExpression=expression '.' memberApplication | foreignName '#' memberApplication) #applyExpression
  | tuple #tupleExpression
  | match # matchExpression;

struct : '{' NL* structStatement (',' NL* structStatement)* (',')? NL* '}' ;
namedStruct : typeIdentifier struct ;
structStatement : use #useStatement
  | TYPEALIAS typeIdentifier typeParameterList? EQUALS (type|sumType) #typeAliasStatement
  | memberName EQUALS (function|expression) #identifierStatement
  | SPREAD varReference #spreadStatement ;

value : NUMBER #intLiteral
      | STRING #stringLiteral
      | TRUE #booleanLiteral
      | FALSE #booleanLiteral ;


// Lexer Rules (tokens)

// Keywords
IF       : 'if' ;
ELSE     : 'else' ;
VARIABLE : 'let' ;
PRINT    : 'print' ;
FOREIGN : 'foreign' ;
USE : 'use' ;
TYPEALIAS : 'type' ;
LOOP : 'loop' ;
MATCH : 'match' ;

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
NOT      : '!' ;
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
SPREAD   : '..' ;

// Identifiers
ID : [_a-zA-Z][$a-zA-Z0-9]* ;
FOREIGN_ID : [_a-zA-Z][$a-zA-Z0-9]* ;
NL : '\n' | 'r' '\n'? ;
WS : [ \t]+ -> skip ;
