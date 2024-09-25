package com.pentlander.sasquach.parser;

import static java.util.Objects.requireNonNullElseGet;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.ast.typenode.BasicTypeNode;
import com.pentlander.sasquach.ast.typenode.FunctionSignature;
import com.pentlander.sasquach.ast.id.ModuleScopedTypeId;
import com.pentlander.sasquach.ast.typenode.NamedTypeNode;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.ast.typenode.StructTypeNode;
import com.pentlander.sasquach.ast.typenode.StructTypeNode.RowModifier;
import com.pentlander.sasquach.ast.typenode.TupleTypeNode;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.ast.id.TypeIdentifier;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import com.pentlander.sasquach.parser.SasquachParser.FunctionTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.NamedTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.StructTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.TupleTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.TypeArgumentListContext;
import com.pentlander.sasquach.parser.SasquachParser.TypeContext;
import com.pentlander.sasquach.type.BuiltinType;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

class TypeVisitor extends com.pentlander.sasquach.parser.SasquachBaseVisitor<TypeNode> implements VisitorHelper {
  private final ModuleContext moduleCtx;
  private final @Nullable QualifiedTypeName structName;

  TypeVisitor(ModuleContext moduleCtx, @Nullable QualifiedTypeName structName) {
    this.moduleCtx = moduleCtx;
    this.structName = structName;
  }

  TypeVisitor(ModuleContext moduleCtx) {
    this(moduleCtx, null);
  }

  @Override
  public TypeNode visitNamedType(NamedTypeContext ctx) {
    var firstTypeId = typeId(ctx.typeIdentifier(0));
    TypeIdentifier typeId = firstTypeId;
    if (ctx.typeIdentifier().size() == 2) {
      typeId = new ModuleScopedTypeId(firstTypeId, typeId(ctx.typeIdentifier(1)));
    } else {
      var builtin = BuiltinType.fromStringOpt(firstTypeId.name().toString());
      if (builtin.isPresent()) {
        return new BasicTypeNode(builtin.get(), rangeFrom(ctx));
      }
    }

    var typeArgs = typeArguments(ctx.typeArgumentList());
    return new NamedTypeNode(typeId, typeArgs, rangeFrom(ctx));
  }

  @Override
  public StructTypeNode visitStructType(StructTypeContext ctx) {
    var fields = new LinkedHashMap<UnqualifiedName, TypeNode>();
    RowModifier rowModifier = RowModifier.none();
    for (var fieldCtx : ctx.structTypeField()) {
      var id = fieldCtx.ID();
      if (fieldCtx.SPREAD() == null) {
        fields.put(new UnqualifiedName(id.getText()),
            VisitorHelper.typeAnnotation(fieldCtx.typeAnnotation(), new TypeVisitor(moduleCtx)));
      } else if (fieldCtx.namedType() != null) {
        rowModifier = RowModifier.namedRow(
            typeId(fieldCtx.namedType()),
            rangeFrom(fieldCtx));
      } else {
        rowModifier = RowModifier.unnamedRow();
      }
    }
    return new StructTypeNode(structName, fields, rowModifier, rangeFrom(ctx));
  }

  @Override
  public FunctionSignature visitFunctionType(FunctionTypeContext ctx) {
    var params = parameterList(this, new ExpressionVisitor(moduleCtx), ctx.functionParameterList());
    return new FunctionSignature(
        params,
        typeNode(ctx.type()),
        rangeFrom(ctx));
  }

  @Override
  public TupleTypeNode visitTupleType(TupleTypeContext ctx) {
    var typeNodes = ctx.type()
        .stream()
        .map(this::typeNode)
        .toList();
    var moduleName = new QualifiedModuleName(new PackageName("std/tuple"), "Tuple");
    var name = requireNonNullElseGet(structName,
        () -> moduleName.qualifyInner(new UnqualifiedTypeName(Integer.toString(typeNodes.size()))));
    return new TupleTypeNode(name, typeNodes, rangeFrom(ctx));
  }

  private List<TypeNode> typeArguments(TypeArgumentListContext ctx) {
    if (ctx == null) {
      return List.of();
    }
    return ctx.type().stream().map(this::typeNode).toList();
  }

  private TypeId typeId(NamedTypeContext ctx) {
    var namedTypeNode = (NamedTypeNode) ctx.accept(this);
    return (TypeId) namedTypeNode.id();
  }

  private TypeNode typeNode(TypeContext ctx) {
    // Need to create a new visitor each time structs will end up with the same name
    return ctx.accept(new TypeVisitor(moduleCtx));
  }

  @Override
  public ModuleContext moduleCtx() {
    return moduleCtx;
  }
}
