package com.pentlander.sasquach.ast.typenode;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public record TupleTypeNode(QualifiedTypeName typeName, List<TypeNode> fields, Range range) implements TypeNode,
    ConstructableNamedTypeNode, VariantTypeNode {

  @Override
  public StructType type() {
    var fieldTypes = new LinkedHashMap<UnqualifiedName, Type>();
    var typeNodes = fields();
    for (int i = 0; i < typeNodes.size(); i++) {
      var typeNode = typeNodes.get(i);
      fieldTypes.put(new UnqualifiedName("_" + i), typeNode.type());
    }
    return new StructType(typeName, fieldTypes);
  }

  @Override
  public String typeNameStr() {
    return typeName.toString();
  }

  @Override
  public String toPrettyString() {
    return fields().stream().map(TypeNode::toPrettyString)
        .collect(Collectors.joining(", ", "(", ")"));
  }
}
