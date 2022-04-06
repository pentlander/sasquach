package com.pentlander.sasquach.ast;

import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import java.util.Map;
import java.util.Map.Entry;

/** Type node for a struct that contains type nodes for all of its fields. */
public record StructTypeNode(Map<String, TypeNode> fieldTypeNodes,
                             Range range) implements TypeNode {
  public StructType type() {
    var fieldTypes = fieldTypeNodes.entrySet().stream()
        .collect(toMap(Entry::getKey, entry -> entry.getValue().type()));
    return new StructType(fieldTypes);
  }
}
