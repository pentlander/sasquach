package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.TypeId;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import com.pentlander.sasquach.type.FunctionType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

public record NamedStruct(UnqualifiedTypeName name, List<Field> fields, Range range) implements
    StructWithName {

  public NamedStruct {
    requireNonNull(name);
    fields = requireNonNullElse(fields, List.of());
    requireNonNull(range);
  }

  @Override
  public List<NamedFunction> functions() {
    return List.of();
  }

  public FunctionCall toFunctionCall() {
    var idRange = new Range.Single(range.sourcePath(), range.start(), name.toString().length());
    var args = fields.stream()
        .map(field -> new Argument(field.name(), field.value(), field.range()))
        .toList();
    return new LocalFunctionCall(new Id(name.toName(), idRange), args, range);
  }
}
