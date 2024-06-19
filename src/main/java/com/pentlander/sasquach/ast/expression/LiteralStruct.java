package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record LiteralStruct(List<Field> fields, List<NamedFunction> functions, List<VarReference> spreads,
                            Range range) implements Struct {

  public LiteralStruct {
    fields = requireNonNullElse(fields, List.of());
    functions = requireNonNullElse(functions, List.of());
    spreads = requireNonNullElse(spreads, List.of());
    requireNonNull(range);
  }

  @Override
  public StructKind structKind() {
    return StructKind.LITERAL;
  }
}
