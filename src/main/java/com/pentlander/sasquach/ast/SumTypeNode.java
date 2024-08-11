package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.StructType;
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
    return typeNameStr() + variantTypeNodes.stream()
        .map(VariantTypeNode::toPrettyString)
        .collect(Collectors.joining());
  }

  public sealed interface VariantTypeNode extends TypeNode {
    QualifiedModuleName moduleName();

    TypeId aliasId();

    TypeId id();

    VariantType type();

    record Singleton(QualifiedModuleName moduleName, TypeId aliasId, TypeId id) implements
        VariantTypeNode {
      @Override
      public Range range() {
        return id.range();
      }

      @Override
      public SingletonType type() {
        return new SingletonType(moduleName().qualifyInner(id().name()));
      }
    }

    record Tuple(QualifiedModuleName moduleName, TypeId aliasId, TypeId id,
                 TupleTypeNode typeNode) implements VariantTypeNode {
      @Override
      public Range range() {
        return id.range().join(typeNode.range());
      }

      @Override
      public StructType type() {
        return new StructType(moduleName.qualifyInner(id.name()), typeNode.type().fieldTypes());
      }
    }

    record Struct(QualifiedModuleName moduleName, TypeId aliasId, TypeId id,
                  StructTypeNode typeNode) implements VariantTypeNode {
      @Override
      public Range range() {
        return id.range().join(typeNode.range());
      }

      @Override
      public StructType type() {
        return new StructType(moduleName.qualifyInner(id.name()), typeNode.type().fieldTypes());
      }
    }
  }
}
