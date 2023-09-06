package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Struct.StructKind;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@RecordBuilder
public record TypedStruct(Optional<String> name, List<Use> useList, List<TypeAlias> typeAliases,
                          List<TypedField> fields, List<NamedFunction> functions,
                          StructKind structKind, Range range) implements
    TypedExpression {

  public StructType type() {
    var fieldTypes = new HashMap<String, Type>();
    functions.forEach(func -> fieldTypes.put(func.name(), func.functionSignature().type()));
    fields.forEach(field -> fieldTypes.put(field.name(), field.type()));
    return name.map(n -> new StructType(n, fieldTypes)).orElseGet(() -> new StructType(fieldTypes));
  }

  public record TypedField(Identifier id, TypedExpression expr) implements TypedExpression {
    public String name() {
      return id.name();
    }

    @Override
    public Type type() {
      return expr.type();
    }

    @Override
    public Range range() {
      return id.range().join(expr.range());
    }
  }
}
