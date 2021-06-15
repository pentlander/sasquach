package com.pentlander.sasquach;

import com.pentlander.sasquach.Range.Multi;
import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.ast.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.tree.TerminalNode;

public class Visitor {
  public static Range rangeFrom(ParserRuleContext context) {
    Token start = context.start;
    Token end = context.stop;
    var pos = new Position(start.getLine(), start.getCharPositionInLine());
    if (start.getLine() == end.getLine()) {
      return new Range.Single(pos, start.getText().length());
    }
    return new Range.Multi(
        pos, new Position(end.getLine(), end.getCharPositionInLine() + end.getText().length()));
  }

  public static Range.Single rangeFrom(Token token) {
    return new Range.Single(
        new Position(token.getLine(), token.getCharPositionInLine()), token.getText().length());
  }

  public static Range.Single rangeFrom(TerminalNode node) {
    return rangeFrom(node.getSymbol());
  }

  static class CompilationUnitVisitor extends SasquachBaseVisitor<CompilationUnit> {
    @Override
    public CompilationUnit visitCompilationUnit(SasquachParser.CompilationUnitContext ctx) {
      String moduleName = ctx.moduleDeclaration().moduleName().getText();
      ModuleVisitor moduleVisitor = new ModuleVisitor();
      ModuleDeclaration module = ctx.moduleDeclaration().accept(moduleVisitor);
      return new CompilationUnit(module);
    }
  }

  static class ModuleVisitor extends SasquachBaseVisitor<ModuleDeclaration> {
    private Scope scope;

    @Override
    public ModuleDeclaration visitModuleDeclaration(SasquachParser.ModuleDeclarationContext ctx) {
      String name = ctx.moduleName().getText();
      var functionSignatureVisitor = new FunctionSignatureVisitor();
      var functionsCtx = ctx.moduleBody().function();
      var metadata = new Metadata(ctx.moduleName().getText());
      scope = new Scope(metadata);
      functionsCtx.stream()
          .map(method -> method.functionDeclaration().accept(functionSignatureVisitor))
          .forEach(scope::addSignature);
      var functions =
          functionsCtx.stream()
              .map(method -> method.accept(new FunctionVisitor(new Scope(metadata, scope))))
              .toList();
      return new ModuleDeclaration(name, functions);
    }
  }

  static class FunctionVisitor extends SasquachBaseVisitor<Function> {
    private final Scope scope;

    FunctionVisitor(Scope scope) {
      this.scope = scope;
    }

    @Override
    public Function visitFunction(SasquachParser.FunctionContext ctx) {
      FunctionSignature funcSignature =
          ctx.functionDeclaration().accept(new FunctionSignatureVisitor());
      funcSignature
          .parameters()
          .forEach(param -> scope.addIdentifier(new Identifier(param.name(), param.type())));

      Block block = ctx.block().accept(new BlockVisitor(scope));

      return new Function(scope, funcSignature, block, rangeFrom(ctx));
    }
  }

  static class ExpressionVisitor extends SasquachBaseVisitor<Expression> {
    private final Scope scope;

    ExpressionVisitor(Scope scope) {
      this.scope = scope;
    }

    @Override
    public Expression visitIdentifier(SasquachParser.IdentifierContext ctx) {
      return scope.findIdentifier(ctx.getText());
    }

    @Override
    public Expression visitValueLiteral(SasquachParser.ValueLiteralContext ctx) {
      String value = ctx.getText();
      var visitor = new TypeVisitor();
      Type type = ctx.accept(visitor);
      return new Value(type, value, rangeFrom(ctx));
    }

    @Override
    public Expression visitParenExpression(SasquachParser.ParenExpressionContext ctx) {
      return ctx.expression().accept(this);
    }

    @Override
    public Expression visitBinaryOperation(SasquachParser.BinaryOperationContext ctx) {
      String operatorString = ctx.operator.getText();
      var visitor = new ExpressionVisitor(scope);
      var leftExpr = ctx.left.accept(visitor);
      var rightExpr = ctx.right.accept(visitor);
      return new BinaryExpression.MathExpression(
          BinaryExpression.Operator.fromString(operatorString), leftExpr, rightExpr);
    }

    @Override
    public Expression visitFunctionCall(SasquachParser.FunctionCallContext ctx) {
      String funName = ctx.functionName().getText();
      FunctionSignature signature = scope.findFunctionSignature(funName);
      List<SasquachParser.ExpressionContext> argExpressions = ctx.expressionList().expression();

      var arguments = new ArrayList<Expression>();
      for (var argExpressionCtx : argExpressions) {
        var visitor = new ExpressionVisitor(scope);
        Expression argument = argExpressionCtx.accept(visitor);
        arguments.add(argument);
      }

      return new FunctionCall(signature, arguments, null, rangeFrom(ctx));
    }

    @Override
    public Expression visitPrintStatement(SasquachParser.PrintStatementContext ctx) {
      Expression expr = ctx.expression().accept(new ExpressionVisitor(scope));
      return new PrintStatement(expr);
    }

    @Override
    public Expression visitIfExpression(SasquachParser.IfExpressionContext ctx) {
      var ifBlock = ctx.ifBlock();
      var ifCondition = ifBlock.ifCondition.accept(this);
      var blockVisitor = new BlockVisitor(scope);
      var trueBlock = ifBlock.trueBlock.accept(blockVisitor);
      Block falseBlock = null;
      if (ifBlock.falseBlock != null) {
        falseBlock = ifBlock.falseBlock.accept(blockVisitor);
      }
      return new IfExpression(ifCondition, trueBlock, falseBlock);
    }

    @Override
    public Expression visitVariableDeclaration(SasquachParser.VariableDeclarationContext ctx) {
      var idName = ctx.identifier().getText();
      Expression expr = ctx.expression().accept(new ExpressionVisitor(scope));
      var identifier = new Identifier(idName, expr.type());
      scope.addIdentifier(identifier);
      return new VariableDeclaration(identifier.name(), expr, ctx.index, rangeFrom(ctx.identifier().ID()));
    }
  }

  static class BlockVisitor extends SasquachBaseVisitor<Block> {
    private final Scope scope;

    BlockVisitor(Scope scope) {
      this.scope = scope;
    }

    @Override
    public Block visitBlock(SasquachParser.BlockContext ctx) {
      List<Expression> expressions =
          ctx.blockStatement().stream()
              .map(blockCtx -> blockCtx.accept(new ExpressionVisitor(scope)))
              .toList();

      Expression returnExpr = null;
      if (ctx.returnExpression != null) {
        returnExpr = ctx.returnExpression.accept(new ExpressionVisitor(scope));
      }

      return new Block(expressions, returnExpr);
    }
  }

  static class TypeVisitor extends SasquachBaseVisitor<Type> {
    @Override
    public Type visitIntLiteral(SasquachParser.IntLiteralContext ctx) {
      return BuiltinType.INT;
    }

    @Override
    public Type visitStringLiteral(SasquachParser.StringLiteralContext ctx) {
      return BuiltinType.STRING;
    }

    @Override
    public Type visitPrimitiveType(SasquachParser.PrimitiveTypeContext ctx) {
      return BuiltinType.fromString(ctx.getText());
    }

    @Override
    public Type visitStructType(SasquachParser.StructTypeContext ctx) {
      return new StructType(ctx.getText());
    }
  }

  static class FunctionSignatureVisitor extends SasquachBaseVisitor<FunctionSignature> {

    @Override
    public FunctionSignature visitFunctionDeclaration(
        SasquachParser.FunctionDeclarationContext ctx) {
      var typeVisitor = new TypeVisitor();
      String funcName = ctx.functionName().getText();

      List<SasquachParser.FunctionArgumentContext> paramsCtx = ctx.functionArgument();
      var params = new ArrayList<FunctionParameter>();
      for (int i = 0; i < paramsCtx.size(); i++) {
        var paramCtx = paramsCtx.get(i);
        var param =
            new FunctionParameter(
                paramCtx.ID().getText(),
                paramCtx.type().accept(typeVisitor),
                i,
                rangeFrom(paramCtx.ID()),
                (Range.Single) rangeFrom(paramCtx.type()));
        params.add(param);
      }

      return new FunctionSignature(
          funcName,
          params,
          ctx.type().accept(typeVisitor),
          rangeFrom(ctx.functionName().ID()),
          rangeFrom(ctx));
    }
  }
}
