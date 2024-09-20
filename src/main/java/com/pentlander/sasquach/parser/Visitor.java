package com.pentlander.sasquach.parser;
import static com.pentlander.sasquach.Util.mapNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.Position;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.*;
import com.pentlander.sasquach.ast.StructTypeNode.RowModifier;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.TypeStatement;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.ast.expression.PipeOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathOperator;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.MemberAccess;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.FunctionCall;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.ast.expression.Match;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Not;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.parser.SasquachParser.*;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * Visitor that parses the source code into an abstract syntax tree.
 */
@NullUnmarked
public class Visitor {
  private final SourcePath sourcePath;
  private final PackageName packageName;

  public Visitor(SourcePath sourcePath, PackageName packageName) {
    this.sourcePath = sourcePath;
    this.packageName = packageName;
  }

  Range rangeFrom(ParserRuleContext context) {
    Token start = context.getStart();
    Token end = context.getStop();
    var pos = new Position(start.getLine(), start.getCharPositionInLine());
    if (start.getLine() == end.getLine()) {
      return new Range.Single(sourcePath,
          pos,
          end.getCharPositionInLine() - start.getCharPositionInLine() + 1);
    }
    return new Range.Multi(sourcePath,
        pos,
        new Position(end.getLine(), end.getCharPositionInLine() + end.getText().length()));
  }

  Range.Single rangeFrom(Token token) {
    return new Range.Single(sourcePath,
        new Position(token.getLine(), token.getCharPositionInLine()),
        token.getText().length());
  }

  Range.Single rangeFrom(TerminalNode node) {
    return rangeFrom(node.getSymbol());
  }

  public CompilationUnitVisitor compilationUnitVisitor() {
    return new CompilationUnitVisitor();
  }

  private enum StructKind {
    LITERAL, MODULE, NAMED,
  }

  public class CompilationUnitVisitor extends
      com.pentlander.sasquach.parser.SasquachBaseVisitor<CompilationUnit> {
    @Override
    public CompilationUnit visitCompilationUnit(CompilationUnitContext ctx) {
      var modules = ctx.moduleDeclaration()
          .stream()
          .map(moduleDecl -> moduleDecl.accept(new ModuleVisitor(packageName)))
          .toList();
      return new CompilationUnit(sourcePath, packageName, modules);
    }
  }

  class ModuleVisitor extends com.pentlander.sasquach.parser.SasquachBaseVisitor<ModuleDeclaration> {
    private final PackageName packageName;

    ModuleVisitor(PackageName packageName) {
      this.packageName = packageName;
    }

    @Override
    public ModuleDeclaration visitModuleDeclaration(ModuleDeclarationContext ctx) {
      var name = new QualifiedModuleName(packageName, ctx.moduleName().getText());
      var struct = (ModuleStruct) ctx.struct().accept(structVisitorForModule(name));
      return new ModuleDeclaration(new QualifiedModuleId(
          name,
          rangeFrom(ctx.moduleName().ID())),
          struct,
          rangeFrom(ctx));
    }
  }

  class FunctionVisitor extends com.pentlander.sasquach.parser.SasquachBaseVisitor<Function> {
    @Override
    public Function visitFunction(FunctionContext ctx) {
      FunctionSignature funcSignature = functionDeclaration(ctx.functionDeclaration());

      var expr = ctx.expression().accept(new ExpressionVisitor());

      return new Function(funcSignature, expr);
    }

    private FunctionSignature functionDeclaration(FunctionDeclarationContext ctx) {
      var typeVisitor = new TypeVisitor();
      var params = parameterList(typeVisitor, ctx.functionParameterList());

      return new FunctionSignature(params,
          typeParams(ctx.typeParameterList()),
          typeAnnotation(ctx.typeAnnotation(), typeVisitor),
          rangeFrom(ctx));
    }
  }

  class ExpressionVisitor extends com.pentlander.sasquach.parser.SasquachBaseVisitor<Expression> {
    @Override
    public Expression visitVarReference(VarReferenceContext ctx) {
      var name = new UnqualifiedName(ctx.getText());
      return new VarReference(new Id(name, rangeFrom(ctx.ID())));
    }

    @Override
    public Expression visitParenExpression(ParenExpressionContext ctx) {
      return ctx.expression().accept(this);
    }

    @Override
    public Expression visitNotExpression(NotExpressionContext ctx) {
      return new Not(ctx.expression().accept(this), rangeFrom(ctx));
    }

    @Override
    public Expression visitBinaryOperation(BinaryOperationContext ctx) {
      String operatorString = ctx.operator.getText();
      var visitor = new ExpressionVisitor();
      var leftExpr = ctx.left.accept(visitor);
      var rightExpr = ctx.right.accept(visitor);
      return new BinaryExpression.MathExpression(
          MathOperator.fromString(operatorString),
          leftExpr,
          rightExpr,
          rangeFrom(ctx));
    }

    @Override
    public Expression visitFunctionCall(FunctionCallContext ctx) {
      var arguments = args(ctx.application());
      var funcId = ctx.functionName().ID();
      if (funcId.getText().equals("recur")) {
        return new Recur(arguments, rangeFrom(ctx));
      }

      return new LocalFunctionCall(id(ctx.functionName().ID()), arguments, rangeFrom(ctx));
    }

    @Override
    public Expression visitCompareExpression(CompareExpressionContext ctx) {
      var leftExpr = ctx.left.accept(this);
      var rightExpr = ctx.right.accept(this);
      return new CompareExpression(
          CompareOperator.fromString(ctx.operator.getText()),
          leftExpr,
          rightExpr,
          rangeFrom(ctx));
    }

    @Override
    public Expression visitBooleanExpression(BooleanExpressionContext ctx) {
      var leftExpr = ctx.left.accept(this);
      var rightExpr = ctx.right.accept(this);
      return new BooleanExpression(
          BooleanOperator.fromString(ctx.operator.getText()),
          leftExpr,
          rightExpr,
          rangeFrom(ctx));
    }

    private ForeignFunctionCall foreignFuncCall(ForeignNameContext foreignNameCtx,
        MemberApplicationContext memberApplicationCtx) {
      var classAlias = typeId(foreignNameCtx);
      var memberId = id(memberApplicationCtx.memberName().ID());
      var arguments = args(memberApplicationCtx.application());
      return new ForeignFunctionCall(classAlias,
          memberId,
          arguments,
          classAlias.range().join(rangeFrom(memberApplicationCtx)));
    }

    @Override
    public Expression visitPipeExpression(PipeExpressionContext ctx) {
      var expr = ctx.expr.accept(this);
      FunctionCall funcCall;
      if (ctx.functionCall() != null) {
        funcCall = (FunctionCall) ctx.functionCall().accept(this);
      } else if (ctx.memberExpression != null) {
        funcCall = memberFuncCall(ctx.memberExpression, ctx.memberApplication());
      } else if (ctx.foreignName() != null) {
        funcCall = foreignFuncCall(ctx.foreignName(), ctx.memberApplication());
      } else {
        throw new IllegalStateException();
      }
      return new PipeOperator(expr, funcCall, rangeFrom(ctx));
    }

    @Override
    public Expression visitPrintStatement(PrintStatementContext ctx) {
      Expression expr = ctx.expression().accept(new ExpressionVisitor());
      return new PrintStatement(expr, rangeFrom(ctx));
    }

    @Override
    public Expression visitIfExpression(IfExpressionContext ctx) {
      var ifBlock = ctx.ifBlock();
      var ifCondition = ifBlock.ifCondition.accept(this);
      var trueExpr = ifBlock.trueBlock.accept(this);
      Expression falseExpr = null;
      if (ifBlock.falseBlock != null) {
        falseExpr = ifBlock.falseBlock.accept(this);
      }
      return new IfExpression(ifCondition, trueExpr, falseExpr, rangeFrom(ctx));
    }

    @Override
    public Expression visitVariableDeclaration(VariableDeclarationContext ctx) {
      var typeAnnotation = typeAnnotation(ctx.typeAnnotation());
      var expr = ctx.expression().accept(new ExpressionVisitor());
      return new VariableDeclaration(id(ctx.ID()), typeAnnotation, expr, rangeFrom(ctx));
    }

    @Override
    public Expression visitStruct(StructContext ctx) {
      return ctx.accept(structVisitorForLiteral());
    }

    @Override
    public Expression visitNamedStruct(NamedStructContext ctx) {
      var namedTypeNode = (NamedTypeNode) ctx.namedType().accept(new TypeVisitor());
      return ctx.struct().accept(structVisitorForNamed(namedTypeNode));
    }
    @Override
    public Expression visitFunction(FunctionContext ctx) {
      return ctx.accept(new FunctionVisitor());
    }

    private List<Argument> args(ApplicationContext ctx) {
      if (ctx.arguments() == null) {
        return List.of();
      }
      return ctx.arguments().argument().stream().map(argCtx -> {
        var label = label(argCtx.label());
        var expr = argCtx.expression().accept(this);
        return new Argument(mapNonNull(label, Id::name), expr, rangeFrom(argCtx));
      }).toList();
    }

    @Override
    public Expression visitMemberApplicationExpression(MemberApplicationExpressionContext ctx) {
      return memberFuncCall(ctx.expression(), ctx.memberApplication());
    }

    private MemberFunctionCall memberFuncCall(ExpressionContext exprCtx,
        MemberApplicationContext memberAppCtx) {
      var expr = exprCtx.accept(this);
      var memberId = id(memberAppCtx.memberName().ID());
      var arguments = args(memberAppCtx.application());
      return new MemberFunctionCall(expr,
          memberId,
          arguments,
          rangeFrom(exprCtx).join(rangeFrom(memberAppCtx)));
    }

    @Override
    public Expression visitMemberAccessExpression(MemberAccessExpressionContext ctx) {
      var expr = ctx.expression().accept(this);
      var memberId = id(ctx.memberName().ID());
      return new MemberAccess(expr, memberId);
    }

    @Override
    public Expression visitForeignMemberAccessExpression(ForeignMemberAccessExpressionContext ctx) {
      var classAliasId = typeId(ctx.foreignName());
      var memberId = id(ctx.memberName().ID());
      return new ForeignFieldAccess(classAliasId, memberId);
    }

    @Override
    public Expression visitForeignMemberApplicationExpression(
        ForeignMemberApplicationExpressionContext ctx) {
      return foreignFuncCall(ctx.foreignName(), ctx.memberApplication());
    }

    @Override
    public Expression visitBlock(BlockContext ctx) {
      var exprVisitor = new ExpressionVisitor();
      List<Expression> expressions = ctx.blockStatement()
          .stream()
          .map(blockCtx -> blockCtx.accept(exprVisitor))
          .toList();
      return new Block(expressions, rangeFrom(ctx));
    }

    @Override
    public Expression visitIntLiteral(IntLiteralContext ctx) {
      return new Value(BuiltinType.INT, ctx.getText(), rangeFrom(ctx));
    }

    @Override
    public Expression visitDblLiteral(DblLiteralContext ctx) {
      return new Value(BuiltinType.DOUBLE, ctx.getText(), rangeFrom(ctx));
    }

    @Override
    public Expression visitStringLiteral(StringLiteralContext ctx) {
      return new Value(BuiltinType.STRING, ctx.getText(), rangeFrom(ctx));
    }

    @Override
    public Expression visitBooleanLiteral(BooleanLiteralContext ctx) {
      return new Value(BuiltinType.BOOLEAN, ctx.getText(), rangeFrom(ctx));
    }

    @Override
    public Expression visitLoopExpression(LoopExpressionContext ctx) {
      var loop = ctx.loop();
      var varDeclCtx = requireNonNullElse(loop.variableDeclaration(),
          List.<VariableDeclarationContext>of());
      var varDecls = varDeclCtx.stream()
          .map(this::visitVariableDeclaration)
          .map(VariableDeclaration.class::cast)
          .toList();
      return new Loop(varDecls, loop.expression().accept(this), rangeFrom(ctx));
    }

    @Override
    public Expression visitTupleExpression(TupleExpressionContext ctx) {
      var tupCtx = ctx.tuple();
      var expressions = tupCtx.expression().stream().map(exprCtx -> exprCtx.accept(this)).toList();
      return com.pentlander.sasquach.ast.expression.Struct.tupleStruct(expressions, rangeFrom(ctx));
    }

    @Override
    public Expression visitMatchExpression(MatchExpressionContext ctx) {
      var match = ctx.match();
      var branches = match.branch().stream().map(branch -> {
        var pattern = switch (branch.pattern()) {
          case SingletonPatternContext pat -> new Pattern.Singleton(typeId(pat.namedType()));
          case SingleTupleVariantPatternContext pat ->
              new Pattern.VariantTuple(typeId(pat.namedType()),
                  List.of(new PatternVariable(id(pat.ID()))),
                  rangeFrom(pat));
          case MultiTupleVariantPatternContext pat ->
              new Pattern.VariantTuple(typeId(pat.namedType()),
                  pat.ID().stream().map(Visitor.this::id).map(PatternVariable::new).toList(),
                  rangeFrom(pat));
          case StructVariantPatternContext pat ->
              new Pattern.VariantStruct(typeId(pat.namedType()),
                  pat.ID().stream().map(Visitor.this::id).map(PatternVariable::new).toList(),
                  rangeFrom(pat));
          default -> throw new IllegalStateException(
              "Unknown match branch: " + branch.pattern().getText());
        };
        return new Branch(pattern, branch.expression().accept(this), rangeFrom(branch));
      }).toList();

      return new Match(match.expression().accept(this), branches, rangeFrom(ctx));
    }
  }

  class TypeVisitor extends com.pentlander.sasquach.parser.SasquachBaseVisitor<TypeNode> {
    private final @Nullable StructName structName;

    TypeVisitor(@Nullable StructName structName) {
      this.structName = structName;
    }

    TypeVisitor() {
      this(null);
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
      var fields = new HashMap<UnqualifiedName, TypeNode>();
      RowModifier rowModifier = RowModifier.none();
      for (var fieldCtx : ctx.structTypeField()) {
        var id = fieldCtx.ID();
        if (fieldCtx.SPREAD() == null) {
          fields.put(
              new UnqualifiedName(id.getText()),
              typeAnnotation(fieldCtx.typeAnnotation(), new TypeVisitor()));
        } else if (fieldCtx.namedType() != null) {
          rowModifier = RowModifier.namedRow(typeId(fieldCtx.namedType()), rangeFrom(fieldCtx));
        } else {
          rowModifier = RowModifier.unnamedRow();
        }
      }
      return new StructTypeNode(structName, fields, rowModifier, rangeFrom(ctx));
    }

    @Override
    public FunctionSignature visitFunctionType(FunctionTypeContext ctx) {
      var params = parameterList(this, ctx.functionParameterList());
      return new FunctionSignature(params, typeNode(ctx.type(), this), rangeFrom(ctx));
    }

    @Override
    public TupleTypeNode visitTupleType(TupleTypeContext ctx) {
      var typeNodes = ctx.type().stream().map(typeCtx -> typeNode(typeCtx, this)).toList();
      return new TupleTypeNode(typeNodes, rangeFrom(ctx));
    }

    private List<TypeNode> typeArguments(TypeArgumentListContext ctx) {
      if (ctx == null) {
        return List.of();
      }
      return ctx.type().stream().map(typeCtx -> typeNode(typeCtx, this)).toList();
    }
  }

  public StructVisitor structVisitorForModule(QualifiedModuleName name) {
    return new StructVisitor(new StructIdentifier.ModuleName(name), StructKind.MODULE);
  }

  public StructVisitor structVisitorForLiteral() {
    // TODO: Set the metadata at the end of the visitStruct func so struct methods work properly
    // TODO: Figure out how to reference parent scope from struct literal
    return new StructVisitor(StructIdentifier.NONE, StructKind.LITERAL);
  }

  public StructVisitor structVisitorForNamed(NamedTypeNode node) {
    // TODO: Set the metadata at the end of the visitStruct func so struct methods work properly
    // TODO: Figure out how to reference parent scope from struct literal
    return new StructVisitor(new StructIdentifier.TypeNode(node), StructKind.NAMED);
  }

  private sealed interface StructIdentifier {
    None NONE = new None();
    record TypeNode(NamedTypeNode node) implements StructIdentifier {}
    record ModuleName(QualifiedModuleName name) implements StructIdentifier {}

    final class None implements StructIdentifier {
      private None() {}
    }
  }

  public class StructVisitor extends com.pentlander.sasquach.parser.SasquachBaseVisitor<Struct> {
    private final StructIdentifier structName;
    private final StructKind structKind;
    private final ExpressionVisitor expressionVisitor;

    private StructVisitor(StructIdentifier structName, StructKind structKind) {
      this.structName = structName;
      this.structKind = structKind;
      this.expressionVisitor = new ExpressionVisitor();
    }

    @Override
    public Struct visitStruct(StructContext ctx) {
      var useList = new ArrayList<Use>();
      var typeStatements = new ArrayList<TypeStatement>();
      var fields = new ArrayList<Field>();
      var functions = new ArrayList<NamedFunction>();
      var spreads = new ArrayList<VarReference>();

      for (var structStatementCtx : ctx.structStatement()) {
        if (structStatementCtx instanceof IdentifierStatementContext idCtx) {
          var fieldName = idCtx.memberName();
          var id = id(fieldName.ID());
          var exprCtx = idCtx.expression();
          var funcCtx = idCtx.function();

          if (exprCtx != null) {
            var expr = exprCtx.accept(expressionVisitor);
            fields.add(new Field(id, expr));
          } else if (funcCtx != null) {
            var func = funcCtx.accept(new FunctionVisitor());
            if (structKind != StructKind.NAMED) {
              functions.add(new NamedFunction(id, func));
            } else {
              fields.add(new Field(id, func));
            }
          }
        } else if (structStatementCtx instanceof UseStatementContext useStatementCtx) {
          var useCtx = useStatementCtx.use();
          var qualifiedName = useCtx.qualifiedName().getText();
          var qualifiedNameIds = useCtx.qualifiedName().ID();
          var aliasNode = qualifiedNameIds.getLast();
          var aliasParts = aliasNode.getText().split("[$.]");
          var aliasId = new Id(new UnqualifiedName(aliasParts[aliasParts.length - 1]), rangeFrom(aliasNode));
          var qualifiedId = QualifiedModuleId.fromString(qualifiedName,
              (Range.Single) rangeFrom(useCtx.qualifiedName()));
          Use use;
          if (useCtx.FOREIGN() != null) {
            use = new Use.Foreign(qualifiedId, aliasId, rangeFrom(useCtx));
          } else {
            use = new Use.Module(qualifiedId, aliasId, rangeFrom(useCtx));
          }
          useList.add(use);
        } else if (structStatementCtx instanceof TypeStatementContext typeStmtCtx) {
          var aliasId = typeId(typeStmtCtx.typeIdentifier());
          var typeParameters = typeParams(typeStmtCtx.typeParameterList());
          var isAlias = typeStmtCtx.TYPEALIAS() != null;
          var moduleName =  ((StructIdentifier.ModuleName) structName).name();
          var namedTypeVisitor = new TypeVisitor(isAlias ? null : moduleName.qualifyInner(aliasId.name()));
          var typeNode = typeStmtCtx.type() != null ? typeNode(typeStmtCtx.type(), namedTypeVisitor)
              : sumTypeNode(typeStmtCtx.sumType(), moduleName, aliasId, typeParameters);
          var typeStatement = new TypeStatement(aliasId, typeParameters, typeNode, isAlias, rangeFrom(typeStmtCtx));
          typeStatements.add(typeStatement);
        } else if (structStatementCtx instanceof SpreadStatementContext spreadCtx) {
          var varRef = (VarReference) spreadCtx.varReference().accept(expressionVisitor);
          spreads.add(varRef);
        }
      }

      return switch (structName) {
        case StructIdentifier.None _ -> com.pentlander.sasquach.ast.expression.Struct.literalStruct(fields, functions, spreads, rangeFrom(ctx));
        case StructIdentifier.ModuleName(var name) -> Struct.moduleStructBuilder(name)
            .useList(useList)
            .typeStatements(typeStatements)
            .fields(fields)
            .functions(functions)
            .range(rangeFrom(ctx))
            .build();
        case StructIdentifier.TypeNode(var node) ->
            Struct.namedStructConstructor((UnqualifiedTypeName) node.id().name(),
                fields,
                rangeFrom(ctx));
      };
    }
  }

  private Id id(TerminalNode node) {
    return new Id(new UnqualifiedName(node.getText()), rangeFrom(node));
  }

  private TypeId typeId(TypeIdentifierContext ctx) {
    var node = ctx.ID();
    return new TypeId(new UnqualifiedTypeName(node.getText()), rangeFrom(node));
  }

  private TypeId typeId(ForeignNameContext ctx) {
    var node = ctx.ID();
    return new TypeId(new UnqualifiedTypeName(node.getText()), rangeFrom(node));
  }

  private TypeId typeId(NamedTypeContext ctx) {
    var namedTypeNode = (NamedTypeNode) ctx.accept(new TypeVisitor());
    return (TypeId) namedTypeNode.id();
  }


  private List<TypeParameter> typeParams(TypeParameterListContext ctx) {
    return Optional.ofNullable(ctx)
        .map(TypeParameterListContext::typeIdentifier)
        .orElse(List.of())
        .stream()
        .map(typeParamCtx -> new TypeParameter(typeId(typeParamCtx)))
        .toList();
  }

  private List<FunctionParameter> parameterList(TypeVisitor typeVisitor,
      FunctionParameterListContext ctx) {
    var params = new ArrayList<FunctionParameter>();
    for (var paramCtx : ctx.functionParameter()) {
      var type = typeAnnotation(paramCtx.typeAnnotation(), typeVisitor);
      var label = label(paramCtx.label());
      var defaultExpr =
          paramCtx.expression() != null ? paramCtx.expression().accept(new ExpressionVisitor())
              : null;
      var param = new FunctionParameter(id(paramCtx.ID()), label, type, defaultExpr);
      params.add(param);
    }
    return params;
  }

  private @Nullable Id label(@Nullable LabelContext labelCtx) {
    return labelCtx != null ? id(labelCtx.ID()) : null;
  }

  private TypeNode sumTypeNode(SumTypeContext ctx, QualifiedModuleName moduleName, TypeId aliasId,
      List<TypeParameter> typeParameters) {
    var numVariants = ctx.typeIdentifier().size();
    var variantNodes = new ArrayList<VariantTypeNode>();
    for (int i = 0; i < numVariants; i++) {
      var id = typeId(ctx.typeIdentifier(i));
      var variantTypeCtx = ctx.variantType(i);
      variantNodes.add(variantTypeNode(moduleName, aliasId, id, variantTypeCtx));
    }
    return new SumTypeNode(moduleName, aliasId, typeParameters, variantNodes, rangeFrom(ctx));
  }

  private VariantTypeNode variantTypeNode(QualifiedModuleName moduleName, TypeId aliasId,
      TypeId id, VariantTypeContext ctx) {
    var structName = id.name();
    return switch (ctx) {
      case SingletonTypeContext ignored -> new VariantTypeNode.Singleton(moduleName, aliasId, id);
      case SingleTupleTypeContext tupCtx -> {
        var typeNode = new TupleTypeNode(structName,
            List.of(typeNode(tupCtx.type(), new TypeVisitor())),
            rangeFrom(ctx));
        yield new VariantTypeNode.Tuple(moduleName, aliasId, id, typeNode);
      }
      case MultiTupleTypeContext tupCtx -> {
        var visitor = new TypeVisitor();
        var typeNodes = tupCtx.type().stream().map(t -> typeNode(t, visitor)).toList();
        var typeNode = new TupleTypeNode(
            structName,
            typeNodes,
            rangeFrom(ctx));
        yield new VariantTypeNode.Tuple(moduleName, aliasId, id, typeNode);
      }
      case StructSumTypeContext structSumCtx -> {
        var typeNode = new TypeVisitor().visitStructType(structSumCtx.structType());
        yield new VariantTypeNode.Struct(moduleName,
            aliasId,
            id,
            new StructTypeNode(structName,
                typeNode.fieldTypeNodes(),
                RowModifier.none(),
                typeNode.range()));
      }
      default -> throw new IllegalStateException();
    };
  }

  private static TypeNode typeAnnotation(TypeAnnotationContext ctx, TypeVisitor visitor) {
    if (ctx == null) return null;
    return typeNode(ctx.type(), visitor);
  }

  private @Nullable TypeNode typeAnnotation(@Nullable TypeAnnotationContext ctx) {
    if (ctx == null) return null;
    return typeNode(ctx.type(), new TypeVisitor());
  }

  private static TypeNode typeNode(TypeContext ctx, TypeVisitor visitor) {
    return ctx.accept(visitor);
  }
}
