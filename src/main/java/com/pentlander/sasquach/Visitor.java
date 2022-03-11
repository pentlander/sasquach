package com.pentlander.sasquach;

import static com.pentlander.sasquach.SasquachParser.ApplicationContext;
import static com.pentlander.sasquach.SasquachParser.BinaryOperationContext;
import static com.pentlander.sasquach.SasquachParser.BlockContext;
import static com.pentlander.sasquach.SasquachParser.ClassTypeContext;
import static com.pentlander.sasquach.SasquachParser.CompilationUnitContext;
import static com.pentlander.sasquach.SasquachParser.ForeignMemberAccessExpressionContext;
import static com.pentlander.sasquach.SasquachParser.FunctionArgumentContext;
import static com.pentlander.sasquach.SasquachParser.FunctionCallContext;
import static com.pentlander.sasquach.SasquachParser.FunctionContext;
import static com.pentlander.sasquach.SasquachParser.FunctionDeclarationContext;
import static com.pentlander.sasquach.SasquachParser.FunctionParameterListContext;
import static com.pentlander.sasquach.SasquachParser.FunctionTypeContext;
import static com.pentlander.sasquach.SasquachParser.IdentifierStatementContext;
import static com.pentlander.sasquach.SasquachParser.IfExpressionContext;
import static com.pentlander.sasquach.SasquachParser.IntLiteralContext;
import static com.pentlander.sasquach.SasquachParser.MemberAccessExpressionContext;
import static com.pentlander.sasquach.SasquachParser.ModuleDeclarationContext;
import static com.pentlander.sasquach.SasquachParser.ParenExpressionContext;
import static com.pentlander.sasquach.SasquachParser.PrimitiveTypeContext;
import static com.pentlander.sasquach.SasquachParser.PrintStatementContext;
import static com.pentlander.sasquach.SasquachParser.StringLiteralContext;
import static com.pentlander.sasquach.SasquachParser.StructContext;
import static com.pentlander.sasquach.SasquachParser.StructTypeContext;
import static com.pentlander.sasquach.SasquachParser.UseStatementContext;
import static com.pentlander.sasquach.SasquachParser.VarReferenceContext;
import static com.pentlander.sasquach.SasquachParser.VariableDeclarationContext;
import static com.pentlander.sasquach.ast.expression.Struct.StructKind;
import static java.util.Objects.*;

import com.pentlander.sasquach.SasquachParser.ApplyExpressionContext;
import com.pentlander.sasquach.SasquachParser.BooleanExpressionContext;
import com.pentlander.sasquach.SasquachParser.BooleanLiteralContext;
import com.pentlander.sasquach.SasquachParser.CompareExpressionContext;
import com.pentlander.sasquach.SasquachParser.ExpressionContext;
import com.pentlander.sasquach.SasquachParser.ForeignMemberApplicationExpressionContext;
import com.pentlander.sasquach.SasquachParser.ForeignNameContext;
import com.pentlander.sasquach.SasquachParser.LocalNamedTypeContext;
import com.pentlander.sasquach.SasquachParser.LoopExpressionContext;
import com.pentlander.sasquach.SasquachParser.MemberApplicationContext;
import com.pentlander.sasquach.SasquachParser.MemberApplicationExpressionContext;
import com.pentlander.sasquach.SasquachParser.ModuleNamedTypeContext;
import com.pentlander.sasquach.SasquachParser.TypeAliasStatementContext;
import com.pentlander.sasquach.SasquachParser.TypeArgumentListContext;
import com.pentlander.sasquach.SasquachParser.TypeContext;
import com.pentlander.sasquach.SasquachParser.TypeParameterListContext;
import com.pentlander.sasquach.ast.BasicTypeNode;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.ModuleScopedIdentifier;
import com.pentlander.sasquach.ast.StructTypeNode;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.expression.ApplyOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanOperator;
import com.pentlander.sasquach.ast.expression.FunctionCall;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedIdentifier;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathOperator;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.ModuleNamedType;
import com.pentlander.sasquach.type.LocalNamedType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Visitor that parses the source code into an abstract syntax tree.
 */
@SuppressWarnings("ClassCanBeRecord")
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
      return new Range.Single(sourcePath, pos,
          end.getCharPositionInLine() - start.getCharPositionInLine() + 1);
    }
    return new Range.Multi(
        sourcePath,
        pos,
        new Position(end.getLine(), end.getCharPositionInLine() + end.getText().length()));
  }

  Range.Single rangeFrom(Token token) {
    return new Range.Single(
        sourcePath,
        new Position(token.getLine(), token.getCharPositionInLine()),
        token.getText().length());
  }

  Range.Single rangeFrom(TerminalNode node) {
    return rangeFrom(node.getSymbol());
  }

  CompilationUnitVisitor compilationUnitVisitor() {
    return new CompilationUnitVisitor();
  }

  class CompilationUnitVisitor extends SasquachBaseVisitor<CompilationUnit> {
    @Override
    public CompilationUnit visitCompilationUnit(CompilationUnitContext ctx) {
      var modules = ctx.moduleDeclaration().stream()
          .map(moduleDecl -> moduleDecl.accept(new ModuleVisitor(
              packageName.toString()))).toList();
      return new CompilationUnit(sourcePath, packageName.toString(), modules);
    }
  }

  class ModuleVisitor extends SasquachBaseVisitor<ModuleDeclaration> {
    private final String packageName;

    ModuleVisitor(String packageName) {
      this.packageName = packageName;
    }

    @Override
    public ModuleDeclaration visitModuleDeclaration(ModuleDeclarationContext ctx) {
      String name = packageName + "/" + ctx.moduleName().getText();
      var struct = ctx.struct().accept(structVisitorForModule(name));
      return new ModuleDeclaration(new QualifiedIdentifier(name, rangeFrom(ctx.moduleName().ID())),
          struct,
          rangeFrom(ctx));
    }
  }

  class FunctionVisitor extends SasquachBaseVisitor<Function> {
    @Override
    public Function visitFunction(FunctionContext ctx) {
      FunctionSignature funcSignature = functionDeclaration(ctx.functionDeclaration());

      var expr = ctx.expression().accept(new ExpressionVisitor());

      return new Function(funcSignature, expr);
    }

    private FunctionSignature functionDeclaration(FunctionDeclarationContext ctx) {
      var typeVisitor = new TypeVisitor();
      var params = parameterList(typeVisitor, ctx.functionParameterList());

      return new FunctionSignature(
          params,
          typeParams(ctx.typeParameterList()),
          typeNode(ctx.type(), typeVisitor),
          rangeFrom(ctx));
    }
  }

  class ExpressionVisitor extends SasquachBaseVisitor<Expression> {
    @Override
    public Expression visitVarReference(VarReferenceContext ctx) {
      var name = ctx.getText();
      return VarReference.of(name, rangeFrom(ctx.ID()));
    }

    @Override
    public Expression visitParenExpression(ParenExpressionContext ctx) {
      return ctx.expression().accept(this);
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
      var classAlias = id(foreignNameCtx.ID());
      var memberId = id(memberApplicationCtx.memberName().ID());
      var arguments = args(memberApplicationCtx.application());
      return new ForeignFunctionCall(classAlias, memberId, arguments,
          classAlias.range().join(rangeFrom(memberApplicationCtx)));
    }

    @Override
    public Expression visitApplyExpression(ApplyExpressionContext ctx) {
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
      return new ApplyOperator(expr, funcCall, rangeFrom(ctx));
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
      Expression expr = ctx.expression().accept(new ExpressionVisitor());
      return new VariableDeclaration(id(ctx.ID()), expr, rangeFrom(ctx));
    }

    @Override
    public Expression visitStruct(StructContext ctx) {
      return ctx.accept(structVisitorForLiteral());
    }

    @Override
    public Expression visitFunction(FunctionContext ctx) {
      return ctx.accept(new FunctionVisitor());
    }

    private List<Expression> args(ApplicationContext ctx) {
      if (ctx.expressionList() == null) {
        return List.of();
      }
      return ctx.expressionList().expression().stream().map(argCtx -> argCtx.accept(this)).toList();
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
      return new FieldAccess(expr, memberId);
    }

    @Override
    public Expression visitForeignMemberAccessExpression(ForeignMemberAccessExpressionContext ctx) {
      var classAliasId = id(ctx.foreignName().ID());
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
      List<Expression> expressions = ctx.blockStatement().stream()
          .map(blockCtx -> blockCtx.accept(exprVisitor)).toList();
      return new Block(expressions, rangeFrom(ctx));
    }

    @Override
    public Expression visitIntLiteral(IntLiteralContext ctx) {
      return new Value(BuiltinType.INT, ctx.getText(), rangeFrom(ctx));
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
      var varDecls = varDeclCtx.stream().map(this::visitVariableDeclaration)
          .map(VariableDeclaration.class::cast).toList();
      return new Loop(varDecls, loop.expression().accept(this), rangeFrom(ctx));
    }
  }

  class TypeVisitor extends SasquachBaseVisitor<TypeNode<? extends Type>> {
    @Override
    public TypeNode<Type> visitPrimitiveType(PrimitiveTypeContext ctx) {
      return new BasicTypeNode<>(BuiltinType.fromString(ctx.getText()), rangeFrom(ctx));
    }

    @Override
    public TypeNode<Type> visitClassType(ClassTypeContext ctx) {
      return new BasicTypeNode<>(new ClassType(ctx.getText()), rangeFrom(ctx));
    }

    @Override
    public TypeNode<Type> visitLocalNamedType(LocalNamedTypeContext ctx) {
      var id = id(ctx.typeIdentifier().ID());
      return new BasicTypeNode<>(
          new LocalNamedType(id, typeArguments(ctx.typeArgumentList())),
          rangeFrom(ctx));
    }

    @Override
    public StructTypeNode visitStructType(StructTypeContext ctx) {
      var fields = new HashMap<String, TypeNode<Type>>();
      for (int i = 0; i < ctx.ID().size(); i++) {
        var id = ctx.ID(i).getText();
        fields.put(id, typeNode(ctx.type(i), new TypeVisitor()));
      }
      return new StructTypeNode(fields, rangeFrom(ctx));
    }

    @Override
    public FunctionSignature visitFunctionType(FunctionTypeContext ctx) {
      var params = parameterList(this, ctx.functionParameterList());
      return new FunctionSignature(params, typeNode(ctx.type(), this), rangeFrom(ctx));
    }

    @Override
    public TypeNode<Type> visitModuleNamedType(ModuleNamedTypeContext ctx) {
      return new BasicTypeNode<>(new ModuleNamedType(
          new ModuleScopedIdentifier(id(ctx.moduleName().ID()), id(ctx.typeIdentifier().ID())),
          typeArguments(ctx.typeArgumentList())), rangeFrom(ctx));
    }

    private List<TypeNode<Type>> typeArguments(TypeArgumentListContext ctx) {
      if (ctx == null) {
        return List.of();
      }
      return ctx.type().stream().map(typeCtx -> typeNode(typeCtx, this)).toList();
    }
  }

  public StructVisitor structVisitorForModule(String name) {
    return new StructVisitor(name, StructKind.MODULE);
  }

  public StructVisitor structVisitorForLiteral() {
    // TODO: Set the metadata at the end of the visitStruct func so struct methods work properly
    // TODO: Figure out how to reference parent scope from struct literal
    return new StructVisitor(null, StructKind.LITERAL);
  }

  class StructVisitor extends SasquachBaseVisitor<Struct> {
    private final String name;
    private final StructKind structKind;
    private final ExpressionVisitor expressionVisitor;

    private StructVisitor(String name, StructKind structKind) {
      this.name = name;
      this.structKind = structKind;
      this.expressionVisitor = new ExpressionVisitor();
    }

    @Override
    public Struct visitStruct(StructContext ctx) {
      var typeVisitor = new TypeVisitor();
      var useList = new ArrayList<Use>();
      var typeAliases = new ArrayList<TypeAlias>();
      var fields = new ArrayList<Field>();
      var functions = new ArrayList<NamedFunction>();
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
            functions.add(new NamedFunction(id, func));
          }
        } else if (structStatementCtx instanceof UseStatementContext useStatementCtx) {
          var useCtx = useStatementCtx.use();
          var qualifiedName = useCtx.qualifiedName().getText();
          var qualifiedNameIds = useCtx.qualifiedName().ID();
          var aliasNode = qualifiedNameIds.get(qualifiedNameIds.size() - 1);
          var aliasId = id(aliasNode);
          var qualifiedId = new QualifiedIdentifier(qualifiedName,
              (Range.Single) rangeFrom(useCtx.qualifiedName()));
          Use use;
          if (useCtx.FOREIGN() != null) {
            use = new Use.Foreign(qualifiedId, aliasId, rangeFrom(useCtx));
          } else {
            use = new Use.Module(qualifiedId, aliasId, rangeFrom(useCtx));
          }
          useList.add(use);
        } else if (structStatementCtx instanceof TypeAliasStatementContext typeAliasStatementContext) {
          var typeAlias = new TypeAlias(
              id(typeAliasStatementContext.typeIdentifier().ID()),
              typeParams(typeAliasStatementContext.typeParameterList()),
              typeNode(typeAliasStatementContext.type(), typeVisitor),
              rangeFrom(typeAliasStatementContext));
          typeAliases.add(typeAlias);
        }
      }

      return switch (structKind) {
        case LITERAL -> Struct.literalStruct(fields, functions, rangeFrom(ctx));
        case MODULE -> Struct.moduleStruct(name,
            useList,
            typeAliases,
            fields,
            functions,
            rangeFrom(ctx));
      };
    }

  }

  private Identifier id(TerminalNode node) {
    return new Identifier(node.getText(), rangeFrom(node));
  }

  private List<TypeParameter> typeParams(TypeParameterListContext ctx) {
    return Optional.ofNullable(ctx).map(TypeParameterListContext::typeIdentifier).orElse(List.of())
        .stream().map(typeParamCtx -> new TypeParameter(id(typeParamCtx.ID()))).toList();
  }

  private List<FunctionParameter> parameterList(TypeVisitor typeVisitor,
      FunctionParameterListContext ctx) {
    var params = new ArrayList<FunctionParameter>();
    for (FunctionArgumentContext paramCtx : ctx.functionArgument()) {
      var type = typeNode(paramCtx.type(), typeVisitor);
      var param = new FunctionParameter(id(paramCtx.ID()), type);
      params.add(param);
    }
    return params;
  }

  @SuppressWarnings("unchecked")
  private static TypeNode<Type> typeNode(TypeContext ctx, TypeVisitor visitor) {
    return (TypeNode<Type>) ctx.accept(visitor);
  }
}
