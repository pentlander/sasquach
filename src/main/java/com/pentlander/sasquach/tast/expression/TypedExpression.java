package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.tast.TypedNode;

public sealed interface TypedExpression extends TypedNode permits Value, TApplyOperator,
    TArrayValue, TBinaryExpression, TBlock, TFieldAccess, TForeignFieldAccess, TFunction,
    TFunctionCall, TIfExpression, TLoop, TMatch, TPrintStatement, TRecur, TStruct,
    TVarReference, TVariableDeclaration, TypedExprWrapper {}
