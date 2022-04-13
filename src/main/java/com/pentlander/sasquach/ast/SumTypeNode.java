package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.VariantType;
import java.util.List;
import java.util.stream.Collectors;

public record SumTypeNode(QualifiedModuleName moduleName, Identifier id,
                          List<VariantTypeNode> variantTypeNodes,
                          Range range) implements TypeNode {
  public SumTypeNode {
    if (variantTypeNodes.size() < 1) {
      throw new IllegalArgumentException("Sum type must have at least one node");
    }
  }

  @Override
  public SumType type() {
    return new SumType(
        moduleName,
        id.name(),
        variantTypeNodes.stream().map(VariantTypeNode::type).toList());
  }

  @Override
  public String typeName() {
    return id.name();
  }

  @Override
  public String toPrettyString() {
    return typeName() + variantTypeNodes.stream().map(VariantTypeNode::toPrettyString)
        .collect(Collectors.joining());
  }

  public sealed interface VariantTypeNode extends TypeNode {
    QualifiedModuleName moduleName();
    Identifier aliasId();
    Identifier id();
    VariantType type();

    record Singleton(QualifiedModuleName moduleName, Identifier aliasId, Identifier id) implements VariantTypeNode {
      @Override
      public Range range() {
        return id.range();
      }

      @Override
      public SingletonType type() {
        return new SingletonType(moduleName(), id().name());
      }
    }

    record Tuple(QualifiedModuleName moduleName, Identifier aliasId, Identifier id,
                 TupleTypeNode typeNode) implements VariantTypeNode {
      @Override
      public Range range() {
        return id.range().join(typeNode.range());
      }

      @Override
      public StructType type() {
        return new StructType(moduleName.qualify(id.name()), typeNode.type().fieldTypes());
      }
    }

    record Struct(QualifiedModuleName moduleName, Identifier aliasId, Identifier id,
                  StructTypeNode typeNode) implements VariantTypeNode {
      @Override
      public Range range() {
        return id.range().join(typeNode.range());
      }

      @Override
      public StructType type() {
        return new StructType(moduleName.qualify(id.name()), typeNode.type().fieldTypes());
      }
    }
  }
}
