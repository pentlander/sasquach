package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.ast.StructName;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.Use;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;
import java.util.stream.Collectors;

@RecordBuilder
public record ModuleStruct(QualifiedModuleName name, List<Use> useList, List<TypeAlias> typeAliases, List<Field> fields, List<NamedFunction> functions,
                           Range range) implements StructWithName {

  public ModuleStruct {
    name = requireNonNull(name);
    useList = requireNonNullElse(useList, List.of());
    typeAliases = requireNonNullElse(typeAliases, List.of());
    fields = requireNonNullElse(fields, List.of());
    functions = requireNonNullElse(functions, List.of());
    requireNonNull(range);
  }

  @Override
  public StructKind structKind() {
    return StructKind.MODULE;
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
