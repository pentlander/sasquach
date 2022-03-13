package com.pentlander.sasquach.ast;

import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public record TupleTypeNode(List<TypeNode<Type>> fields, Range range) implements TypeNode<StructType> {
  @Override
  public StructType type() {
    var fieldTypes = new HashMap<String, Type>();
    var typeNodes = fields();
    for (int i = 0; i < typeNodes.size(); i++) {
      var typeNode = typeNodes.get(i);
      fieldTypes.put("_" + i, typeNode.type());
    }
    return new StructType(fieldTypes);
  }

  @Override
  public String typeName() {
    return "Tuple" + fields.size();
  }

  @Override
  public String toPrettyString() {
    return fields().stream().map(TypeNode::toPrettyString)
        .collect(Collectors.joining(", ", "(", ")"));
  }
}
