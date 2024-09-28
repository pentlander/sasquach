package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.name.QualifiedTypeName;
import java.util.List;

// TODO: Switch name to id
public record NamedStruct(QualifiedTypeName name, List<Field> fields, Range range) implements
    StructWithName {

  public NamedStruct {
    requireNonNull(name);
    requireNonNull(fields);
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
    return new LocalFunctionCall(new Id(name.simpleName().toName(), idRange), args, range);
  }
}
