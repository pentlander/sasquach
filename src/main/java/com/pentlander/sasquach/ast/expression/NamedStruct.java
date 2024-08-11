package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record NamedStruct(UnqualifiedTypeName name, List<Field> fields, List<NamedFunction> functions,
                          Range range) implements StructWithName {

  public NamedStruct {
    requireNonNull(name);
    fields = requireNonNullElse(fields, List.of());
    functions = requireNonNullElse(functions, List.of());
    requireNonNull(range);
  }

  @Override
  public StructKind structKind() {
    return StructKind.NAMED;
  }
}
