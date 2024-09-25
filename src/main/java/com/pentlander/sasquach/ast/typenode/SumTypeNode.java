package com.pentlander.sasquach.ast.typenode;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode.Singleton;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.TypeParameter;
import com.pentlander.sasquach.type.VariantType;
import java.util.List;
import java.util.stream.Collectors;

public record SumTypeNode(QualifiedModuleName moduleName, TypeId id,
                          List<TypeParameter> typeParameters,
                          List<VariantTypeNode> variantTypeNodes, Range range) implements TypeNode {
  public SumTypeNode {
    if (variantTypeNodes.isEmpty()) {
      throw new IllegalArgumentException("Sum type must have at least one node");
    }
  }

  @Override
  public SumType type() {
    return new SumType(
        moduleName.qualifyInner(id.name()),
        typeParameters,
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
      Singleton, TupleTypeNode, StructTypeNode {
    QualifiedTypeName typeName();

    VariantType type();

    record Singleton(QualifiedModuleName moduleName, TypeId aliasId, TypeId id) implements
        VariantTypeNode {
      @Override
      public Range range() {
        return id.range();
      }

      @Override
      public QualifiedTypeName typeName() {
        return moduleName.qualifyInner(id.name());
      }

      @Override
      public SingletonType type() {
        return new SingletonType(moduleName().qualifyInner(id().name()));
      }
    }
  }
}
