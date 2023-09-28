package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.tast.expression.TFunction;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TNamedFunction(Identifier id, TFunction function) implements TypedNode, TypedMember {
  public String name() {
    return id().name();
  }

  public Range.Single nameRange() {
    return id.range();
  }

  public List<FunctionParameter> parameters() {
    return function.parameters();
  }

  public TypedExpression expression() {
    return function.expression();
  }

  @Override
  public FunctionType type() {
    return function.type();
  }

  @Override
  public Range range() {
    return id.range().join(function.range());
  }

  public String toPrettyString() {
    return "%s = %s".formatted(id().name(), function.toPrettyString());
  }
}
