package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Node;

public sealed interface Expression extends Node permits ArrayValue, BinaryExpression, Block,
    ForeignFieldAccess, Function, FunctionCall, IfExpression, Loop, Match, MemberAccess, Not,
    PipeOperator, PrintStatement, Recur, Struct, Value, VarReference,
    VariableDeclaration {
  Range range();
}

// PrintStatement, VarDecl, VarRef, Value, ArrayValue, LocalFuncCall, BinaryExpr, IfExpr, Struct,
// FieldAccess, Block, ForeignFieldAccess, ForeignFuncCall, MemberFuncCall
