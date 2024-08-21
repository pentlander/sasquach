package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.ast.StructName;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TLocalFunctionCall(UnqualifiedName name, TargetKind targetKind,
                                 List<TypedExpression> arguments, FunctionType functionType,
                                 Type returnType, Range range) implements TFunctionCall {

  public sealed interface TargetKind {
    record QualifiedFunction(QualifiedModuleId ownerId) implements TargetKind {}

    record LocalVariable(TLocalVariable localVariable) implements TargetKind {}

    record VariantStructConstructor(StructName name) implements TargetKind {}
  }
}
