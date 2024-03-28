package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;
import java.util.Optional;

@RecordBuilder
public record NamedStruct(String name, List<Field> fields, List<NamedFunction> functions,
                          Range range) implements StructWithName {

  public NamedStruct {
    name = requireNonNull(name);
    fields = requireNonNullElse(fields, List.of());
    functions = requireNonNullElse(functions, List.of());
    requireNonNull(range);
  }

  @Override
  public StructKind structKind() {
    return StructKind.NAMED;
  }
}
