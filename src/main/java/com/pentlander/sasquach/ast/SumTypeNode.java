package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.TypeParameter;
import com.pentlander.sasquach.type.VariantType;
import java.util.List;
import java.util.stream.Collectors;

public record SumTypeNode(QualifiedModuleName moduleName, Id id,
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
        moduleName,
        id.name(),
        typeParameters,
        variantTypeNodes.stream().map(VariantTypeNode::type).toList());
  }

  @Override
  public String typeName() {
    return id.name();
  }

  @Override
  public String toPrettyString() {
    return typeName() + variantTypeNodes.stream()
        .map(VariantTypeNode::toPrettyString)
        .collect(Collectors.joining());
  }

  public sealed interface VariantTypeNode extends TypeNode {
    QualifiedModuleName moduleName();

    Id aliasId();

    Id id();

    VariantType type();

    record Singleton(QualifiedModuleName moduleName, Id aliasId, Id id) implements
        VariantTypeNode {
      @Override
      public Range range() {
        return id.range();
      }

      @Override
      public SingletonType type() {
        return new SingletonType(moduleName(), id().name());
      }
    }

    record Tuple(QualifiedModuleName moduleName, Id aliasId, Id id,
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

    record Struct(QualifiedModuleName moduleName, Id aliasId, Id id,
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
