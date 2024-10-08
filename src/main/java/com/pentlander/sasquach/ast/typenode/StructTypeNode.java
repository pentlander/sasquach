package com.pentlander.sasquach.ast.typenode;

import static com.pentlander.sasquach.Util.toLinkedMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.TypeIdentifier;
import com.pentlander.sasquach.ast.typenode.StructTypeNode.RowModifier.NamedRow;
import com.pentlander.sasquach.ast.typenode.StructTypeNode.RowModifier.None;
import com.pentlander.sasquach.ast.typenode.StructTypeNode.RowModifier.UnnamedRow;
import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.TypeParameterNode;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

/**
 * Type node for a struct that contains type nodes for all of its fields.
 * <p>
 * It can appear as follows:
 * <code>
 * typealias NoName = { foo: String }
 * type Named = { foo: String }
 * { func = (foo: T }
 * </code>
 *
 */
public record StructTypeNode(@Nullable QualifiedTypeName typeName,
                             List<TypeParameterNode> typeParams,
                             SequencedMap<UnqualifiedName, TypeNode> fieldTypeNodes, RowModifier rowModifier,
                             Range range) implements TypeNode, ConstructableNamedTypeNode,
    VariantTypeNode {

  @Override
  public StructType type() {
    var typeParams = typeParams().stream().map(TypeParameterNode::toTypeParameter).toList();
    var fieldTypes = fieldTypeNodes.entrySet().stream()
        .collect(toLinkedMap(Entry::getKey, entry -> entry.getValue().type()));
    var rowModifier = switch (rowModifier()) {
      case NamedRow namedRow -> new StructType.RowModifier.NamedRow(namedRow.typeNode().type());
      case None _ -> StructType.RowModifier.none();
      case UnnamedRow _ -> StructType.RowModifier.unnamedRow();
    };
    return new StructType(typeName, typeParams, fieldTypes, rowModifier);
  }

  @Override
  public String typeNameStr() {
    return Objects.requireNonNullElse(typeName, type().name()).toString();
  }

  public sealed interface RowModifier {
    record NamedRow(NamedTypeNode typeNode) implements RowModifier {
      public String name() {
        return typeNode.type().typeNameStr();
      }
    }

    static NamedRow namedRow(TypeIdentifier id, Range range) {
      return new NamedRow(new NamedTypeNode(id, List.of(), range));
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
