grammar Sasquach;

// parser rules
compilationUnit : moduleDeclaration EOF;
moduleDeclaration : moduleName '{' moduleBody '}' ;

moduleBody: function* ;
moduleName: ID;

function : functionDeclaration '{' (blockStatement)* '}' ;
functionDeclaration locals [ int paramIndex ] : FUNCTION functionName '(' (functionArgument[ $paramIndex++ ] ',')* '):' type ;
functionName : ID ;
functionArgument [ int index ] : ID ':' type ;

type : primitiveType | structType ;
primitiveType : 'boolean' | 'string' ('[' ']')* | 'char' | 'byte' | 'int' | 'long' | 'float' | 'double' | 'void' ;
structType : QUALIFIED_NAME ;

blockStatement locals [ int lastVarIndex ]: variableDeclaration[ $lastVarIndex++ ] | printStatement | functionCall ;

variableDeclaration [ int index ] : VARIABLE identifier EQUALS expression ;
identifier : ID ;
printStatement : PRINT expression ;
functionCall : functionName '(' expressionList ')' ;

expressionList : expression (',' expression)* ;
expression : identifier | value | functionCall ;
value : NUMBER
      | STRING ;

// lexer rules (tokens)
FUNCTION : 'fn' ;
VARIABLE : 'let' ;
PRINT : 'print' ;
EQUALS : '=' ;
NUMBER : [0-9]+ ;
STRING : '"'.*'"' ;
ID : [a-zA-Z0-9]+ ;
QUALIFIED_NAME : ID ('.' ID)+;
WS : [ \t\n\r]+ -> skip ;