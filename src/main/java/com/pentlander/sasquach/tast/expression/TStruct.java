package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.Struct.StructKind;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.tast.TypedMember;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@RecordBuilder
public record TStruct(Optional<String> name, List<Use> useList, List<TypeAlias> typeAliases,
                      List<TField> fields, List<TNamedFunction> functions, StructKind structKind,
                      Range range) implements TypedExpression {
  public TStruct {
    name = requireNonNullElse(name, Optional.empty());
    useList = requireNonNullElse(useList, List.of());
    typeAliases = requireNonNullElse(typeAliases, List.of());
    requireNonNull(fields, "fields");
    requireNonNull(functions, "functions");
    requireNonNull(structKind, "structKind");
    requireNonNull(range, "range");
  }

  public StructType type() {
    var fieldTypes = new LinkedHashMap<String, Type>();
    functions.forEach(func -> fieldTypes.put(func.name(), func.type()));
    fields.forEach(field -> fieldTypes.put(field.name(), field.type()));
    return name.map(n -> new StructType(n, fieldTypes)).orElseGet(() -> new StructType(fieldTypes));
  }

  public record TField(Identifier id, TypedExpression expr) implements TypedNode, TypedMember {
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
