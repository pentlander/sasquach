package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.ast.QualifiedTypeName;
import com.pentlander.sasquach.ast.StructName;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.tast.expression.TLocalFunctionCall.TargetKind;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record TConstructorCall(QualifiedTypeName variantName,
                               List<Integer> argIndexes,
                               List<TypedExpression> arguments, FunctionType functionType,
                               Type returnType, Range range) implements TFunctionCall {

  @Override
  public UnqualifiedName name() {
    return new UnqualifiedName(variantName.name().toString());
  }
}
