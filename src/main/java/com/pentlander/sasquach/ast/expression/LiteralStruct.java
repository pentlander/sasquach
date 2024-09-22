package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import java.util.List;

public record LiteralStruct(List<Field> fields, List<NamedFunction> functions, List<VarReference> spreads,
                            Range range) implements Struct {

  public LiteralStruct {
    fields = requireNonNullElse(fields, List.of());
    functions = requireNonNullElse(functions, List.of());
    spreads = requireNonNullElse(spreads, List.of());
    requireNonNull(range);
  }
}
