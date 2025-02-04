package com.pentlander.sasquach.ast.typenode;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode.SingletonTypeNode;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.TypeParameterNode;
import com.pentlander.sasquach.type.VariantType;
import java.util.List;
import java.util.stream.Collectors;

public record SumTypeNode(QualifiedModuleName moduleName, TypeId id,
                          List<TypeParameterNode> typeParameterNodes,
                          List<? extends VariantTypeNode> variantTypeNodes, Range range) implements TypeNode {
  public SumTypeNode {
    if (variantTypeNodes.isEmpty()) {
      throw new IllegalArgumentException("Sum type must have at least one node");
    }
  }

  @Override
  public SumType type() {
    return new SumType(
        id.name(),
        typeParameterNodes.stream().map(TypeParameterNode::toTypeParameter).toList(),
        variantTypeNodes.stream().map(VariantTypeNode::type).toList());
  }

  @Override
  public String typeNameStr() {
    return id.name().toString();
  }

  @Override
  public String toPrettyString() {
    return id().name().toString() + variantTypeNodes.stream()
        .map(VariantTypeNode::toPrettyString)
        .collect(Collectors.joining());
  }

  public sealed interface VariantTypeNode extends TypeNode, ConstructableNamedTypeNode permits
      SingletonTypeNode, TupleTypeNode, StructTypeNode {
    QualifiedTypeName typeName();

    VariantType type();

    record SingletonTypeNode(TypeId id) implements
        VariantTypeNode {
      @Override
      public Range range() {
        return id.range();
      }

      @Override
      public QualifiedTypeName typeName() {
        return id.name();
      }

      @Override
      public SingletonType type() {
        return new SingletonType(id().name());
      }
    }
  }
}
