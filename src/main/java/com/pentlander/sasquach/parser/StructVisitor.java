package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.Use.Foreign;
import com.pentlander.sasquach.ast.Use.Module;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.id.QualifiedModuleId;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.ast.typenode.SumTypeNode;
import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode.Singleton;
import com.pentlander.sasquach.ast.typenode.TupleTypeNode;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.ast.typenode.TypeStatement;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import com.pentlander.sasquach.parser.SasquachParser.IdentifierStatementContext;
import com.pentlander.sasquach.parser.SasquachParser.MultiTupleTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.SingleTupleTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.SingletonTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.SpreadStatementContext;
import com.pentlander.sasquach.parser.SasquachParser.StructContext;
import com.pentlander.sasquach.parser.SasquachParser.StructStatementContext;
import com.pentlander.sasquach.parser.SasquachParser.StructSumTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.SumTypeContext;
import com.pentlander.sasquach.parser.SasquachParser.TypeStatementContext;
import com.pentlander.sasquach.parser.SasquachParser.UseStatementContext;
import com.pentlander.sasquach.parser.SasquachParser.VariantTypeContext;
import com.pentlander.sasquach.parser.StructIdentifier.ModuleName;
import com.pentlander.sasquach.parser.StructIdentifier.None;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StructVisitor extends
    com.pentlander.sasquach.parser.SasquachBaseVisitor<Struct> implements VisitorHelper {
  private final StructIdentifier structName;
  private final ExpressionVisitor expressionVisitor;
  private final ModuleContext moduleCtx;

  StructVisitor(ModuleContext moduleCtx, StructIdentifier structName) {
    this.moduleCtx = moduleCtx;
    this.structName = structName;
    this.expressionVisitor = new ExpressionVisitor(moduleCtx);
  }

  @SuppressWarnings("unchecked")
  private static <T extends StructStatementContext> List<T> getCtx(
      Map<? extends Class<? extends StructStatementContext>, List<StructStatementContext>> stmtCtxByClass,
      Class<T> clazz
  ) {
    return (List<T>) stmtCtxByClass.getOrDefault(clazz, List.of());
  }

  @Override
  public Struct visitStruct(StructContext ctx) {
    var useList = new ArrayList<Use>();
    var typeStatements = new ArrayList<TypeStatement>();
    var fields = new ArrayList<Field>();
    var functions = new ArrayList<NamedFunction>();
    var spreads = new ArrayList<VarReference>();

    var statementCtxByClass = ctx.structStatement()
        .stream()
        .collect(Collectors.groupingBy(StructStatementContext::getClass));

    for (var stmtCtx : getCtx(statementCtxByClass, UseStatementContext.class)) {
      var useCtx = stmtCtx.use();
      var qualifiedName = useCtx.qualifiedName().getText();
      var qualifiedNameIds = useCtx.qualifiedName().ID();
      var aliasNode = qualifiedNameIds.getLast();
      var aliasParts = aliasNode.getText().split("[$.]");
      var aliasId = new Id(new UnqualifiedName(aliasParts[aliasParts.length - 1]),
          rangeFrom(aliasNode));
      var qualifiedId = QualifiedModuleId.fromString(qualifiedName,
          (Single) rangeFrom(useCtx.qualifiedName()));
      Use use;
      if (useCtx.FOREIGN() != null) {
        use = new Foreign(qualifiedId, aliasId, rangeFrom(useCtx));
        moduleCtx.putTypeName(
            aliasId.name().toTypeName(),
            qualifiedId.name().toQualifiedTypeName());
      } else {
        use = new Module(qualifiedId, aliasId, rangeFrom(useCtx));
        moduleCtx.putModuleName(aliasId.name(), qualifiedId.moduleName());
      }
      useList.add(use);
    }

    for (var stmtCtx : getCtx(statementCtxByClass, TypeStatementContext.class)) {
      var name = new UnqualifiedTypeName(stmtCtx.typeIdentifier().ID().getText());
      var moduleName = (ModuleName) structName;
      moduleCtx.putTypeName(name, moduleName.name().qualifyInner(name));
    }

    for (var structStatementCtx : ctx.structStatement()) {
      switch (structStatementCtx) {
        case IdentifierStatementContext idCtx -> {
          var fieldName = idCtx.memberName();
          var id = id(fieldName.ID());
          var exprCtx = idCtx.expression();
          var funcCtx = idCtx.function();

          if (exprCtx != null) {
            var expr = exprCtx.accept(expressionVisitor);
            fields.add(new Field(id, expr));
          } else if (funcCtx != null) {
            var func = funcCtx.accept(new FunctionVisitor(moduleCtx));
            if (!(structName instanceof StructIdentifier.TypeNode)) {
              functions.add(new NamedFunction(id, func));
            } else {
              fields.add(new Field(id, func));
            }
          }
        }
        case UseStatementContext _ -> {}
        case TypeStatementContext typeStmtCtx -> {
          var aliasId = typeId(typeStmtCtx.typeIdentifier());
          var typeParameters = typeParams(typeStmtCtx.typeParameterList());
          var isAlias = typeStmtCtx.TYPEALIAS() != null;
          var moduleName = ((ModuleName) structName).name();
          var namedTypeVisitor = new TypeVisitor(moduleCtx, isAlias ? null : aliasId.name());
          var typeNode =
              typeStmtCtx.type() != null ? VisitorHelper.typeNode(typeStmtCtx.type(), namedTypeVisitor)
                  : sumTypeNode(typeStmtCtx.sumType(), moduleName, aliasId, typeParameters);
          var typeStatement = new TypeStatement(aliasId,
              typeParameters,
              typeNode,
              isAlias,
              rangeFrom(typeStmtCtx));
          typeStatements.add(typeStatement);
        }
        case SpreadStatementContext spreadCtx -> {
          var varRef = (VarReference) spreadCtx.varReference().accept(expressionVisitor);
          spreads.add(varRef);
        }
        case null, default -> {
        }
      }
    }

    return switch (structName) {
      case None _ ->
          Struct.literalStruct(fields, functions, spreads, rangeFrom(ctx));
      case ModuleName(var name) -> Struct.moduleStructBuilder(name)
          .useList(useList)
          .typeStatements(typeStatements)
          .fields(fields)
          .functions(functions)
          .range(rangeFrom(ctx))
          .build();
      case StructIdentifier.TypeNode(var node) ->
          Struct.namedStructConstructor((QualifiedTypeName) node.id().name(),
              fields,
              rangeFrom(ctx));
    };
  }


  private TypeNode sumTypeNode(
      SumTypeContext ctx, QualifiedModuleName moduleName, TypeId aliasId,
      List<TypeParameter> typeParameters) {
    var numVariants = ctx.typeIdentifier().size();
    var variantNodes = new ArrayList<VariantTypeNode>();
    for (int i = 0; i < numVariants; i++) {
      var id = ctx.typeIdentifier(i).ID();
      var name = moduleName.qualifyInner(new UnqualifiedTypeName(id.getText()));
      var typeId = new TypeId(name, rangeFrom(id));
      var variantTypeCtx = ctx.variantType(i);
      variantNodes.add(variantTypeNode(moduleName, aliasId, typeId, variantTypeCtx));
    }
    return new SumTypeNode(moduleName, aliasId, typeParameters, variantNodes, rangeFrom(ctx));
  }

  private VariantTypeNode variantTypeNode(QualifiedModuleName moduleName, TypeId aliasId,
      TypeId id, VariantTypeContext ctx) {
    var qualifiedStructName = id.name();
    moduleCtx.putTypeName(qualifiedStructName.simpleName(), qualifiedStructName);
    return switch (ctx) {
      case SingletonTypeContext ignored -> new Singleton(moduleName, aliasId, id);
      case SingleTupleTypeContext tupCtx -> new TupleTypeNode(qualifiedStructName,
          List.of(VisitorHelper.typeNode(tupCtx.type(), new TypeVisitor(moduleCtx))),
          rangeFrom(ctx));
      case MultiTupleTypeContext tupCtx -> {
        var typeVisitor = new TypeVisitor(moduleCtx);
        var typeNodes = tupCtx.type().stream().map(t -> VisitorHelper.typeNode(t, typeVisitor)).toList();
        yield new TupleTypeNode(
            qualifiedStructName,
            typeNodes,
            rangeFrom(ctx));
      }
      case StructSumTypeContext structSumCtx ->
          new TypeVisitor(moduleCtx, qualifiedStructName).visitStructType(structSumCtx.structType());
      default -> throw new IllegalStateException();
    };
  }

  @Override
  public ModuleContext moduleCtx() {
    return moduleCtx;
  }

  @Override
  public SourcePath sourcePath() {
    return moduleCtx.sourcePath();
  }
}
