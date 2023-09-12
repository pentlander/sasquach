package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.tast.expression.TypedStruct.TypedField;

public sealed interface TypedExpression extends TypedNode permits TFunctionCall,
    TypeCheckedFunction, TypedExprWrapper, TypedStruct, TypedField {}
