package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TLocalFunctionCall(Identifier functionId, TargetKind targetKind,
                                 List<TypedExpression> arguments, FunctionType functionType,
                                 Type returnType, Range range) implements TFunctionCall {

  public String name() {
    return functionId.name();
  }

  @Override
  public Type type() {
    return returnType;
  }

  public sealed interface TargetKind {
    record QualifiedFunction(QualifiedModuleId ownerId) implements TargetKind {}

    record LocalVariable(TLocalVariable localVariable) implements TargetKind {}

    record VariantStructConstructor(TStruct struct) implements TargetKind {}
  }
}
