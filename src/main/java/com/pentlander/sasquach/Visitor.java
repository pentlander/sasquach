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
import static com.pentlander.sasquach.SasquachParser.TypeIdentifierContext;
import static com.pentlander.sasquach.SasquachParser.UseStatementContext;
import static com.pentlander.sasquach.SasquachParser.VarReferenceContext;
import static com.pentlander.sasquach.SasquachParser.VariableDeclarationContext;
import static com.pentlander.sasquach.ast.expression.Struct.StructKind;

import com.pentlander.sasquach.SasquachParser.BooleanLiteralContext;
import com.pentlander.sasquach.SasquachParser.CompareExpressionContext;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.FunctionParameter;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Metadata;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedIdentifier;
import com.pentlander.sasquach.ast.Scope;
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
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.NamedType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Visitor that parses the source code into an abstract syntax tree.
 */
public class Visitor {
  public static Range rangeFrom(ParserRuleContext context) {
    Token start = context.getStart();
    Token end = context.getStop();
    var pos = new Position(start.getLine(), start.getCharPositionInLine());
    if (start.getLine() == end.getLine()) {
      return new Range.Single(pos, end.getCharPositionInLine() - start.getCharPositionInLine() + 1);
    }
    return new Range.Multi(
        pos,
        new Position(end.getLine(), end.getCharPositionInLine() + end.getText().length()));
  }

  public static Range.Single rangeFrom(Token token) {
    return new Range.Single(
        new Position(token.getLine(), token.getCharPositionInLine()),
        token.getText().length());
  }

  public static Range.Single rangeFrom(TerminalNode node) {
    return rangeFrom(node.getSymbol());
  }

  static class CompilationUnitVisitor extends SasquachBaseVisitor<CompilationUnit> {
    private final String packageName;

    CompilationUnitVisitor(String packageName) {
      this.packageName = packageName;
    }

    @Override
    public CompilationUnit visitCompilationUnit(CompilationUnitContext ctx) {
      var modules = ctx.moduleDeclaration().stream()
          .map(moduleDecl -> moduleDecl.accept(new ModuleVisitor(packageName))).toList();
      return new CompilationUnit(modules);
    }
  }

  static class ModuleVisitor extends SasquachBaseVisitor<ModuleDeclaration> {
    private final String packageName;

    ModuleVisitor(String packageName) {
      this.packageName = packageName;
    }

    @Override
    public ModuleDeclaration visitModuleDeclaration(ModuleDeclarationContext ctx) {
      String name = packageName + "." + ctx.moduleName().getText();
      var struct = ctx.struct().accept(StructVisitor.forModule(name));
      return new ModuleDeclaration(new Identifier(name, rangeFrom(ctx.moduleName().ID())),
          struct,
          rangeFrom(ctx));
    }
  }

  static class FunctionVisitor extends SasquachBaseVisitor<Function> {
    private final Scope scope;
    private final Identifier id;

    FunctionVisitor(Scope scope, Identifier id) {
      this.scope = Scope.forBlock(scope);
      this.id = id;
    }

    @Override
    public Function visitFunction(FunctionContext ctx) {
      FunctionSignature funcSignature = functionDeclaration(ctx.functionDeclaration());
      funcSignature.typeParameters()
          .forEach(param -> scope.addNamedType(param.type().typeName(), param));
      funcSignature.parameters().forEach(param -> scope.addLocalIdentifier(param.id()));

      var expr = ctx.expression().accept(new ExpressionVisitor(scope));

      return new Function(scope, id, funcSignature, expr);
    }

    private FunctionSignature functionDeclaration(FunctionDeclarationContext ctx) {
      var typeVisitor = new TypeVisitor(scope);

      var params = parameterList(typeVisitor, ctx.functionParameterList());

      var typeParams = ctx.typeIdentifier().stream()
          .map(typeParamCtx -> new TypeNode(new NamedType(typeParamCtx.ID().getText()),
              rangeFrom(typeParamCtx.ID()))).toList();

      return new FunctionSignature(params,
          typeParams,
          ctx.type().accept(typeVisitor),
          rangeFrom(ctx));
    }
  }

  static class ExpressionVisitor extends SasquachBaseVisitor<Expression> {
    private final Scope scope;

    ExpressionVisitor(Scope scope) {
      this.scope = scope;
    }

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
      var visitor = new ExpressionVisitor(scope);
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
    public Expression visitPrintStatement(PrintStatementContext ctx) {
      Expression expr = ctx.expression().accept(new ExpressionVisitor(scope));
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
      Expression expr = ctx.expression().accept(new ExpressionVisitor(scope));
      scope.addLocalIdentifier(id(ctx.ID()));
      return new VariableDeclaration(id(ctx.ID()), expr, 1, rangeFrom(ctx));
    }

    @Override
    public Expression visitStruct(StructContext ctx) {
      return ctx.accept(StructVisitor.forLiteral(scope));
    }

    private List<Expression> args(ApplicationContext ctx) {
      return ctx.expressionList().expression().stream().map(argCtx -> argCtx.accept(this)).toList();
    }

    @Override
    public Expression visitMemberAccessExpression(MemberAccessExpressionContext ctx) {
      var expr = ctx.expression().accept(this);
      var memberId = id(ctx.memberName().ID());
      if (ctx.application() != null) {
        var arguments = args(ctx.application());
        return new MemberFunctionCall(expr, memberId, arguments, rangeFrom(ctx));
      } else {
        return new FieldAccess(expr, memberId);
      }
    }

    @Override
    public Expression visitForeignMemberAccessExpression(ForeignMemberAccessExpressionContext ctx) {
      var classAliasId = id(ctx.foreignName().ID());
      var memberId = id(ctx.memberName().ID());
      if (ctx.application() != null) {
        var arguments = args(ctx.application());
        return new ForeignFunctionCall(classAliasId, memberId, arguments, rangeFrom(ctx));
      } else {
        return new ForeignFieldAccess(classAliasId, memberId);
      }
    }

    @Override
    public Expression visitBlock(BlockContext ctx) {
      var blockScope = Scope.forBlock(scope);
      var exprVisitor = new ExpressionVisitor(blockScope);
      List<Expression> expressions = ctx.blockStatement().stream()
          .map(blockCtx -> blockCtx.accept(exprVisitor)).toList();
      return new Block(blockScope, expressions, rangeFrom(ctx));
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
  }

  static class TypeVisitor extends SasquachBaseVisitor<TypeNode> {
    private final Scope scope;

    TypeVisitor(Scope scope) {
      this.scope = scope;
    }

    @Override
    public TypeNode visitPrimitiveType(PrimitiveTypeContext ctx) {
      return new TypeNode(BuiltinType.fromString(ctx.getText()), rangeFrom(ctx));
    }

    @Override
    public TypeNode visitClassType(ClassTypeContext ctx) {
      return new TypeNode(new ClassType(ctx.getText()), rangeFrom(ctx));
    }

    @Override
    public TypeNode visitTypeIdentifier(TypeIdentifierContext ctx) {
      return new TypeNode(new NamedType(ctx.getText()), rangeFrom(ctx));
    }

    @Override
    public TypeNode visitStructType(StructTypeContext ctx) {
      var fields = new HashMap<String, Type>();
      for (int i = 0; i < ctx.ID().size(); i++) {
        var id = ctx.ID(i).getText();
        var typeNode = ctx.type(i).accept(new TypeVisitor(Scope.forStructType(scope)));
        fields.put(id, typeNode.type());
      }
      return new TypeNode(new StructType(fields), rangeFrom(ctx));
    }

    @Override
    public TypeNode visitFunctionType(FunctionTypeContext ctx) {
      var params = parameterList(this, ctx.functionParameterList());
      var type = new FunctionType(params.stream().map(FunctionParameter::type).toList(),
          List.of(),
          ctx.type().accept(this).type());
      return new TypeNode(type, rangeFrom(ctx));
    }
  }

  static class StructVisitor extends SasquachBaseVisitor<Struct> {
    private final Scope scope;
    private final String name;
    private final StructKind structKind;
    private final ExpressionVisitor expressionVisitor;

    private StructVisitor(Scope scope, String name, StructKind structKind) {
      this.scope = scope;
      this.name = name;
      this.structKind = structKind;
      this.expressionVisitor = new ExpressionVisitor(scope);
    }

    public static StructVisitor forModule(String name) {
      return new StructVisitor(Scope.topLevel(new Metadata(name)), name, StructKind.MODULE);
    }

    public static StructVisitor forLiteral(Scope parentScope) {
      // TODO: Set the metadata at the end of the visitStruct func so struct methods work properly
      // TODO: Figure out how to reference parent scope from struct literal
      return new StructVisitor(Scope.forStructLiteral(parentScope), null, StructKind.LITERAL);
    }

    @Override
    public Struct visitStruct(StructContext ctx) {
      var useList = new ArrayList<Use>();
      var fields = new ArrayList<Field>();
      var functions = new ArrayList<Function>();
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
            var func = funcCtx.accept(new FunctionVisitor(scope, id));
            scope.addFunction(func);
            functions.add(func);
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
          scope.addUse(use);
          useList.add(use);
        }
      }

      return switch (structKind) {
        case LITERAL -> Struct.literalStruct(scope, fields, functions, rangeFrom(ctx));
        case MODULE -> Struct.moduleStruct(scope, name, useList, fields, functions, rangeFrom(ctx));
      };
    }

  }

  private static Identifier id(TerminalNode node) {
    return new Identifier(node.getText(), rangeFrom(node));
  }

  private static List<FunctionParameter> parameterList(TypeVisitor typeVisitor,
      FunctionParameterListContext ctx) {
    var params = new ArrayList<FunctionParameter>();
    for (FunctionArgumentContext paramCtx : ctx.functionArgument()) {
      var type = paramCtx.type().accept(typeVisitor);
      var param = new FunctionParameter(id(paramCtx.ID()), type);
      params.add(param);
    }
    return params;
  }
}
