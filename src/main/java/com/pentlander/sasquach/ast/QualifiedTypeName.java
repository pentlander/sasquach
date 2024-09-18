package com.pentlander.sasquach.ast;

import static com.pentlander.sasquach.Util.concat;
import static com.pentlander.sasquach.Util.conj;
import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.Preconditions;
import java.util.List;
import java.util.Objects;

public record QualifiedTypeName(QualifiedModuleName qualifiedModuleName,
                                List<UnqualifiedTypeName> names) implements Name, StructName,
    QualifiedName {
  public QualifiedTypeName {
    Preconditions.checkArgument(!names.isEmpty(), "names must not be empty");
  }

  public QualifiedTypeName(QualifiedModuleName qualifiedModuleName, UnqualifiedTypeName name) {
    this(qualifiedModuleName, List.of(name));
  }

  public UnqualifiedTypeName name() {
    return names.getLast();
  }

  public QualifiedTypeName qualify(UnqualifiedTypeName name) {
    return new QualifiedTypeName(qualifiedModuleName, conj(names, name));
  }

  @Override
  public String toString() {
    return qualifiedModuleName.toString() + "$" + names.stream()
        .map(Objects::toString)
        .collect(joining("$"));
  }

  @Override
  public String toPrettyString() {
    return qualifiedModuleName.toString() + "." + names.stream()
        .map(Objects::toString)
        .collect(joining("."));
  }
}
