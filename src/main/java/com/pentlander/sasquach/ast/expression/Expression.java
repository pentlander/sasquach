package com.pentlander.sasquach.ast.expression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Node;

public sealed interface Expression extends Node permits ArrayValue, BinaryExpression, Block,
    FieldAccess, ForeignFieldAccess, Function, FunctionCall, IfExpression, PrintStatement,
    Struct,
    Struct.Field, Value, VarReference, VariableDeclaration {
  @JsonIgnore
  Range range();
}

// PrintStatement, VarDecl, VarRef, Value, ArrayValue, LocalFuncCall, BinaryExpr, IfExpr, Struct,
// FieldAccess, Block, ForeignFieldAccess, ForeignFuncCall, MemberFuncCall