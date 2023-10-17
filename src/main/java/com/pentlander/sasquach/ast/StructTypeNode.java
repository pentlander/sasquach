package com.pentlander.sasquach.ast;

import static com.pentlander.sasquach.Util.toLinkedMap;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/** Type node for a struct that contains type nodes for all of its fields. */
public record StructTypeNode(String name, Map<String, TypeNode> fieldTypeNodes,
                             Range range) implements TypeNode {

  public StructTypeNode(Map<String, TypeNode> fieldTypeNodes, Range range) {
    this(null, fieldTypeNodes, range);
  }

  @Override
  public StructType type() {
    var fieldTypes = fieldTypeNodes.entrySet().stream()
        .collect(toLinkedMap(Entry::getKey, entry -> entry.getValue().type()));
    return new StructType(name, fieldTypes);
  }

  @Override
  public String typeName() {
    return Objects.requireNonNullElse(name, type().typeName());
  }
}
