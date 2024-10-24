grammar Sasquach;

// Parser Rules
compilationUnit : NL* moduleDeclaration+ EOF;
moduleDeclaration : moduleName struct NL* ;

moduleName: ID;

qualifiedName : ID ('/' ID)+;
use: USE FOREIGN? qualifiedName ;

block : '{' NL* blockStatement? (NL+ blockStatement)*  NL* '}' ;
function : functionDeclaration expression ;
typeParameterList : ('[' typeIdentifier (',' typeIdentifier)* ']') ;
functionDeclaration :
    typeParameterList?
    functionParameterList typeAnnotation? '->' NL* ;
functionName : ID ;
label: ID ;
functionParameter : label? ID typeAnnotation? (EQUALS expression)? ;
functionParameterList : '(' (functionParameter)? (',' functionParameter)* ')' ;

type : structType | namedType | functionType | tupleType ;
structTypeField : NL* ID typeAnnotation | SPREAD namedType? ;
structType : '{' structTypeField (',' structTypeField)* ','? NL* '}' ;
functionType : functionParameterList '->' type ;
typeArgumentList : ('[' type (',' type)* ']') ;
namedType: typeIdentifier ('.' typeIdentifier)? typeArgumentList? ;
tupleType : '(' type (',' type)* ')' ;
sumType : ('|' typeIdentifier variantType )+ ;
variantType :
    () #singletonType
  | '(' type ')' #singleTupleType
  | '(' type (',' NL* type)+ ')' #multiTupleType
  | structType #structSumType;
typeAnnotation : ':' type ;

blockStatement : variableDeclaration | printStatement | expression ;

variableDeclaration : LET ID typeAnnotation? EQUALS expression ;
typeIdentifier: ID ;
varReference : ID ;
memberName : ID ;
foreignName: ID ;
printStatement : PRINT expression ;
ifBlock : IF ifCondition=expression trueBlock=expression (ELSE falseBlock=expression)? ;
functionCall : functionName application ;
loop : LOOP '(' variableDeclaration? (',' NL* variableDeclaration)* ')' '->' NL* expression ;

pattern :
    namedType #singletonPattern
  | namedType '(' ID ')' #singleTupleVariantPattern
  | namedType '(' ID (',' NL* ID)+ ')' #multiTupleVariantPattern
  | namedType '{' NL* ID (',' NL* ID)* (',')? NL* '}' #structVariantPattern ;
branch : pattern '->' expression ;
match : MATCH expression '{' NL* (branch ',' NL*)+ '}' ;

argument : (label EQUALS)? expression ;
arguments : NL? argument (',' NL? argument)* NL? ;
application :  LP arguments? RP
  | LP arguments? {notifyErrorListeners("Missing closing ')'");} ;
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
  | expr=expression NL* PIPE NL* (functionCall | memberExpression=expression '.' memberApplication | foreignName '#' memberApplication) #pipeExpression
  | tuple #tupleExpression
  | match # matchExpression;

struct : '{' NL* structStatement (',' NL* structStatement)* (',')? NL* '}' ;
namedStruct : namedType struct ;
structStatement : use #useStatement
  | typeKeyword=(TYPEDEF|TYPEALIAS) typeIdentifier typeParameterList? EQUALS (type|sumType) #typeStatement
  | memberName EQUALS (function|expression) #identifierStatement
  | SPREAD varReference #spreadStatement ;

decLiteral : DEC_DIGIT (DEC_DIGIT)* ;
intLiteral : decLiteral ;
dblLiteral : DEC_DIGIT '.' DEC_DIGIT+ ;

value : intLiteral #integerLiteral
      | dblLiteral #doubleLiteral
      | STRING #stringLiteral
      | TRUE #booleanLiteral
      | FALSE #booleanLiteral ;


// Lexer Rules (tokens)

// Keywords
IF       : 'if' ;
ELSE     : 'else' ;
LET : 'let' ;
PRINT    : 'print' ;
FOREIGN : 'foreign' ;
USE : 'use' ;
TYPEDEF : 'type' ;
TYPEALIAS : 'typealias' ;
LOOP : 'loop' ;
MATCH : 'match' ;

// Literals
DEC_DIGIT : [0-9] ;
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
PIPE    : '|>' ;
SPREAD   : '..' ;

// Identifiers
ID : [_a-zA-Z][$a-zA-Z0-9]* ;
FOREIGN_ID : [_a-zA-Z][$a-zA-Z0-9]* ;
NL : '\n' | '\r' '\n'? ;
WS : [ \t]+ -> skip ;
COMMENT : '//' ~[\r\n]* NL -> skip ;
