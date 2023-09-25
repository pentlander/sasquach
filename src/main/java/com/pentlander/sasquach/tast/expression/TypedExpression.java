package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.tast.expression.TStruct.TField;

public sealed interface TypedExpression extends TypedNode permits Value, TApplyOperator,
    TArrayValue, TBinaryExpression, TBlock, TFieldAccess, TForeignFieldAccess, TFunction,
    TFunctionCall, TIfExpression, TLocalVariable, TLoop, TMatch, TPrintStatement, TRecur,
    TVarReference, TypedExprWrapper, TStruct, TField {}
