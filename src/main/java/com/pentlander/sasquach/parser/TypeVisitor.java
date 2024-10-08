package com.pentlander.sasquach.parser;

import static java.util.Objects.requireNonNullElseGet;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Tuple;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.ast.id.TypeIdentifier;
import com.pentlander.sasquach.ast.id.TypeParameterId;
import com.pentlander.sasquach.ast.typenode.ArrayTypeNode;
import com.pentlander.sasquach.ast.typenode.BasicTypeNode;
import com.pentlander.sasquach.ast.typenode.FunctionSignature;
import com.pentlander.sasquach.ast.typenode.NamedTypeNode;
import com.pentlander.sasquach.ast.typenode.StructTypeNode;
import com.pentlander.sasquach.ast.typenode.StructTypeNode.RowModifier;
import com.pentlander.sasquach.ast.typenode.TupleTypeNode;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import com.pentlander.sasquach.nameres.NameNotFoundError;
import com.pentlander.sasquach.parser.SasquachParser.FunctionTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.NamedTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.StructTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.TupleTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.TypeArgumentListContext;
import com.pentlander.sasquach.parser.SasquachParser.TypeContext;
import com.pentlander.sasquach.type.ArrayType;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.TypeParameterNode;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

class TypeVisitor extends com.pentlander.sasquach.parser.SasquachBaseVisitor<TypeNode> implements VisitorHelper {
  private final ModuleContext moduleCtx;
  private final @Nullable QualifiedTypeName structName;
  private final List<TypeParameterNode> typeParams;

  TypeVisitor(ModuleContext moduleCtx, @Nullable QualifiedTypeName structName,
      List<TypeParameterNode> typeParams
  ) {
    this.moduleCtx = moduleCtx;
    this.structName = structName;
    this.typeParams = typeParams;
  }


  TypeVisitor(ModuleContext moduleCtx, List<TypeParameterNode> typeParams) {
    this(moduleCtx, null, typeParams);
  }

  TypeVisitor(ModuleContext moduleCtx) {
    this(moduleCtx, null, List.of());
  }

  @Override
  public TypeNode visitNamedType(NamedTypeContext ctx) {
    var firstTypeIdCtx = ctx.typeIdentifier(0);
    TypeIdentifier typeId;
    var typeArgs = typeArguments(ctx.typeArgumentList());

    if (ctx.typeIdentifier().size() == 1) {
      var id = firstTypeIdCtx.ID();
      var builtin = BuiltinType.fromStringOpt(id.getText());
      if (builtin.isPresent()) {
        return new BasicTypeNode(builtin.get(), rangeFrom(ctx));
      } else if (id.getText().equals(ArrayType.TYPE_NAME)) {
        return new ArrayTypeNode(typeArgs.getFirst(), rangeFrom(ctx));
      }

      var name = new UnqualifiedTypeName(id.getText());
      var qualName = moduleCtx().getTypeName(name);
      var range = rangeFrom(id);
      if (qualName != null) {
        typeId = new TypeId(qualName, range);
      } else {
        if (typeParams.stream().anyMatch(param -> param.name().equals(name))) {
          typeId = new TypeParameterId(name, range);
        } else {
          moduleCtx.addError(new NameNotFoundError(name, range, "type", List.of()));
          var fakeName = moduleCtx.moduleName().qualifyInner(name);
          typeId = new TypeId(fakeName, range);
        }
      }
    } else {
      var moduleId = id(firstTypeIdCtx.ID());
      var qualModuleName = moduleCtx.getModuleName(moduleId);
      var name = new UnqualifiedTypeName(ctx.typeIdentifier(1).ID().getText());
      typeId = new TypeId(new QualifiedTypeName(qualModuleName, name), (Range.Single) rangeFrom(ctx));
    }

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
            VisitorHelper.typeAnnotation(fieldCtx.typeAnnotation(), new TypeVisitor(moduleCtx, typeParams)));
      } else if (fieldCtx.namedType() != null) {
        rowModifier = RowModifier.namedRow(
            typeId(fieldCtx.namedType()),
            rangeFrom(fieldCtx));
      } else {
        rowModifier = RowModifier.unnamedRow();
      }
    }
    return new StructTypeNode(structName, typeParams, fields, rowModifier, rangeFrom(ctx));
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
    var name = requireNonNullElseGet(structName, () -> Tuple.tupleName(typeNodes.size()));
    return new TupleTypeNode(name, typeNodes, rangeFrom(ctx));
  }

  private List<TypeNode> typeArguments(TypeArgumentListContext ctx) {
    if (ctx == null) {
      return List.of();
    }
    return ctx.type().stream().map(this::typeNode).toList();
  }

  private TypeIdentifier typeId(NamedTypeContext ctx) {
    var namedTypeNode = (NamedTypeNode) ctx.accept(this);
    return namedTypeNode.id();
  }

  private TypeNode typeNode(TypeContext ctx) {
    // Need to create a new visitor each time structs will end up with the same name
    return ctx.accept(new TypeVisitor(moduleCtx, typeParams));
  }

  @Override
  public ModuleContext moduleCtx() {
    return moduleCtx;
  }
}
