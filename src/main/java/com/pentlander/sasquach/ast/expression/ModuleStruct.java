package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.ast.TypeStatement;
import com.pentlander.sasquach.ast.Use;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

@RecordBuilder
public record ModuleStruct(QualifiedModuleName name, List<Use> useList, List<TypeStatement> typeStatements, List<Field> fields, List<NamedFunction> functions,
                           Range range) implements StructWithName {

  public ModuleStruct(
      QualifiedModuleName name,
      @Nullable List<Use> useList,
      @Nullable List<TypeStatement> typeStatements,
      @Nullable List<Field> fields,
      @Nullable List<NamedFunction> functions,
      Range range
  ) {
    this.name = requireNonNull(name);
    this.useList = requireNonNullElse(useList, List.of());
    this.typeStatements = requireNonNullElse(typeStatements, List.of());
    this.fields = requireNonNullElse(fields, List.of());
    this.functions = requireNonNullElse(functions, List.of());
    this.range = requireNonNull(range);
  }

  @Override
  public String toPrettyString() {
    return "{" + useList().stream()
        .map(Node::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + fields().stream()
        .map(Field::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + functions().stream()
        .map(NamedFunction::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + "}";
  }
}
