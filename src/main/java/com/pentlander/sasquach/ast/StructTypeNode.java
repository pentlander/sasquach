package com.pentlander.sasquach.ast;

import static com.pentlander.sasquach.Util.toLinkedMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.StructTypeNode.RowModifier.NamedRow;
import com.pentlander.sasquach.ast.StructTypeNode.RowModifier.None;
import com.pentlander.sasquach.ast.StructTypeNode.RowModifier.UnnamedRow;
import com.pentlander.sasquach.type.LocalNamedType;
import com.pentlander.sasquach.type.StructType;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Type node for a struct that contains type nodes for all of its fields. */
public record StructTypeNode(@Nullable String name, Map<String, TypeNode> fieldTypeNodes, RowModifier rowModifier,
                             Range range) implements TypeNode {

  public StructTypeNode(Map<String, TypeNode> fieldTypeNodes, RowModifier rowModifier, Range range) {
    this(null, fieldTypeNodes, rowModifier, range);
  }

  @Override
  public StructType type() {
    var fieldTypes = fieldTypeNodes.entrySet().stream()
        .collect(toLinkedMap(Entry::getKey, entry -> entry.getValue().type()));
    var rowModifier = switch (rowModifier()) {
      case NamedRow namedRow -> new StructType.RowModifier.NamedRow(namedRow.typeNode().type());
      case None none -> StructType.RowModifier.none();
      case UnnamedRow unnamedRow -> StructType.RowModifier.unnamedRow();
    };
    return new StructType(name, fieldTypes, rowModifier);
  }

  @Override
  public String typeName() {
    return Objects.requireNonNullElse(name, type().typeName());
  }

  public sealed interface RowModifier {
    record NamedRow(BasicTypeNode<LocalNamedType> typeNode) implements RowModifier {
      public String name() {
        return typeNode.typeName();
      }
    }

    static NamedRow namedRow(Identifier id, Range range) {
      return new NamedRow(new BasicTypeNode<>(new LocalNamedType(id), range));
    }

    final class UnnamedRow implements RowModifier {
      private static final UnnamedRow INSTANCE = new UnnamedRow();
      private UnnamedRow() {}
    }

    static UnnamedRow unnamedRow() {
      return UnnamedRow.INSTANCE;
    }

    final class None implements RowModifier {
      private static final None INSTANCE = new None();
      private None() {}
    }

    static None none() {
      return None.INSTANCE;
    }
  }
}
