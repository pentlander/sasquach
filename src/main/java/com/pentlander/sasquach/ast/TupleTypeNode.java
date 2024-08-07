package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public record TupleTypeNode(@Nullable UnqualifiedStructName name, List<TypeNode> fields, Range range) implements TypeNode {

  public TupleTypeNode(List<TypeNode> fields, Range range) {
    this(null, fields, range);
  }

  @Override
  public StructType type() {
    var fieldTypes = new LinkedHashMap<String, Type>();
    var typeNodes = fields();
    for (int i = 0; i < typeNodes.size(); i++) {
      var typeNode = typeNodes.get(i);
      fieldTypes.put("_" + i, typeNode.type());
    }
    return new StructType(name, fieldTypes);
  }

  @Override
  public String typeName() {
    return name != null ? name.toString() : "Tuple" + fields.size();
  }

  @Override
  public String toPrettyString() {
    return fields().stream().map(TypeNode::toPrettyString)
        .collect(Collectors.joining(", ", "(", ")"));
  }
}
