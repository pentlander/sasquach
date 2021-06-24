package com.pentlander.sasquach;

import com.pentlander.sasquach.SasquachParser.BooleanLiteralContext;
import com.pentlander.sasquach.SasquachParser.CompareExpressionContext;
import com.pentlander.sasquach.SasquachParser.FieldAccessContext;
import com.pentlander.sasquach.SasquachParser.StructLiteralContext;
import com.pentlander.sasquach.ast.*;
import com.pentlander.sasquach.ast.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.BinaryExpression.CompareOperator;
import com.pentlander.sasquach.ast.BinaryExpression.MathOperator;
import com.pentlander.sasquach.ast.Struct.Field;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import org.antlr.v4.runtime.tree.TerminalNode;

import static com.pentlander.sasquach.SasquachParser.*;
import static com.pentlander.sasquach.ast.ForeignFunctionCall.*;
import static com.pentlander.sasquach.ast.Struct.*;

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
    public CompilationUnit visitCompilationUnit(CompilationUnitContext ctx) {
      ModuleVisitor moduleVisitor = new ModuleVisitor();
      ModuleDeclaration module = ctx.moduleDeclaration().accept(moduleVisitor);
      return new CompilationUnit(module);
    }
  }

  static class ModuleVisitor extends SasquachBaseVisitor<ModuleDeclaration> {
    @Override
    public ModuleDeclaration visitModuleDeclaration(ModuleDeclarationContext ctx) {
      String name = ctx.moduleName().getText();
      var struct = ctx.struct().accept(StructVisitor.forModule(name));
      return new ModuleDeclaration(name, struct);
    }
  }

  static class FunctionVisitor extends SasquachBaseVisitor<Function> {
    private final Scope scope;
    private final Identifier id;

    FunctionVisitor(Scope scope, Identifier id) {
      this.scope = scope;
      this.id = id;
    }

    @Override
    public Function visitFunction(FunctionContext ctx) {
      FunctionSignature funcSignature =
          ctx.functionDeclaration().accept(new FunctionSignatureVisitor());
      funcSignature
          .parameters()
          .forEach(param -> scope.addIdentifier(param.id(), param.type()));

      var expr = ctx.expression().accept(new ExpressionVisitor(scope));

      return new Function(scope, id, funcSignature, expr);
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
      return VarReference.of(name, scope.findIdentifierType(name).orElseThrow(), rangeFrom(ctx.ID()));
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
          MathOperator.fromString(operatorString), leftExpr, rightExpr, rangeFrom(ctx));
    }

    @Override
    public Expression visitFunctionCall(FunctionCallContext ctx) {
      String funcName = ctx.functionName().getText();
      var function = scope.findFunction(funcName);
      List<ExpressionContext> argExpressions = ctx.expressionList().expression();

      var arguments = new ArrayList<Expression>();
      for (var argExpressionCtx : argExpressions) {
        var visitor = new ExpressionVisitor(scope);
        Expression argument = argExpressionCtx.accept(visitor);
        arguments.add(argument);
      }

      var id = new Identifier(funcName, rangeFrom(ctx.functionName().ID()));
      return new FunctionCall(id, function.functionSignature(), arguments, null, rangeFrom(ctx));
    }

    @Override
    public Expression visitCompareExpression(CompareExpressionContext ctx) {
      var leftExpr = ctx.left.accept(this);
      var rightExpr = ctx.right.accept(this);
      return new CompareExpression(
          CompareOperator.fromString(ctx.operator.getText()), leftExpr, rightExpr, rangeFrom(ctx));
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
      var idName = ctx.ID().getText();
      Expression expr = ctx.expression().accept(new ExpressionVisitor(scope));
      var identifier = new Identifier(idName, rangeFrom(ctx.ID()));
      scope.addIdentifier(identifier, expr.type());
      return new VariableDeclaration(identifier, expr, ctx.index, rangeFrom(ctx));
    }

    @Override
    public Expression visitStruct(StructContext ctx) {
      return ctx.accept(StructVisitor.forLiteral(scope));
    }

    @Override
    public Expression visitFieldAccess(FieldAccessContext ctx) {
      var expr = ctx.expression().accept(this);
      return FieldAccess.of(expr, ctx.fieldName().getText(), rangeFrom(ctx.fieldName().ID()));
    }

    @Override
    public Expression visitFunctionAccess(FunctionAccessContext ctx) {
      var classAlias = ctx.varReference().getText();
      String funcName = ctx.functionCall().functionName().getText();
      List<ExpressionContext> argExpressions = ctx.functionCall().expressionList().expression();
      var arguments = new ArrayList<Expression>();
      for (var argExpressionCtx : argExpressions) {
        var visitor = new ExpressionVisitor(scope);
        Expression argument = argExpressionCtx.accept(visitor);
        arguments.add(argument);
      }
      var use = scope.findUse(classAlias).orElseThrow();
      if (use instanceof Use.Foreign foreignUse) {
        Type classType = new ClassType(foreignUse.qualifiedName());
        List<Class<?>> argClasses = arguments.stream().map(arg -> arg.type().typeClass()).collect(Collectors.toList());
        String name = funcName;
        Type returnType = null;
        String methodDescriptor = null;
        FunctionCallType callType = null;
        if (funcName.equals("new")) {
          returnType = classType;
          callType = FunctionCallType.SPECIAL;
          try {
            name = "<init>";
            var handle = MethodHandles.lookup().findConstructor(classType.typeClass(), MethodType.methodType(void.class,
                    argClasses));
            methodDescriptor = handle.type().changeReturnType(void.class).toMethodDescriptorString();
          } catch (NoSuchMethodException|IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        } else {
          try {
            var method = classType.typeClass().getMethod(funcName,
                    argClasses.stream().skip(1).toList().toArray(new Class<?>[]{}));
            callType = Modifier.isStatic(method.getModifiers()) ? FunctionCallType.STATIC : FunctionCallType.VIRTUAL;
            var handle = MethodHandles.lookup().unreflect(method).type();
            if (callType == FunctionCallType.VIRTUAL) {
              // Drop the "this" for the call since it's implied by the owner
              handle = handle.dropParameterTypes(0, 1);
            }
            methodDescriptor = handle.toMethodDescriptorString();
            returnType = new ClassType(handle.returnType().getName());
          } catch (NoSuchMethodException|IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        }
        var id = new Identifier(name, (Range.Single) foreignUse.range());
        return new ForeignFunctionCall(id, arguments, methodDescriptor, callType, returnType,
                classType.internalName(),
                rangeFrom(ctx));
      }
      throw new IllegalStateException();
    }

    @Override
    public Expression visitBlock(BlockContext ctx) {
      var blockScope = new Scope(scope);
      var exprVisitor = new ExpressionVisitor(blockScope);
      List<Expression> expressions =
              ctx.blockStatement().stream()
                      .map(blockCtx -> blockCtx.accept(exprVisitor))
                      .toList();

      Expression returnExpr = null;
      if (ctx.returnExpression != null) {
        returnExpr = ctx.returnExpression.accept(exprVisitor);
      }

      return new Block(blockScope, expressions, returnExpr, rangeFrom(ctx));
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
    @Override
    public TypeNode visitPrimitiveType(PrimitiveTypeContext ctx) {
      return new TypeNode(BuiltinType.fromString(ctx.getText()), rangeFrom(ctx));
    }

    @Override
    public TypeNode visitClassType(ClassTypeContext ctx) {
      return new TypeNode(new ClassType(ctx.getText()), rangeFrom(ctx));
    }

    @Override
    public TypeNode visitStructType(StructTypeContext ctx) {
      var fields = new HashMap<String, Type>();
      for (int i = 0; i < ctx.ID().size(); i++) {
        var id = ctx.ID(i).getText();
        var typeNode = ctx.type(i).accept(this);
        fields.put(id, typeNode.type());
      }
      return new TypeNode(new StructType(fields), rangeFrom(ctx));
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
      return new StructVisitor(new Scope(new Metadata(name)), name, StructKind.MODULE);
    }

    public static StructVisitor forLiteral(Scope parentScope) {
      return new StructVisitor(new Scope(new Metadata("null")), null, StructKind.LITERAL);
    }

    @Override
    public Struct visitStruct(StructContext ctx) {
      var useList = new ArrayList<Use>();
      var fields = new ArrayList<Field>();
      var functions = new ArrayList<Function>();
      for (var structStatementCtx : ctx.structStatement()) {
        if (structStatementCtx instanceof IdentifierStatementContext idCtx) {
            var fieldName = idCtx.fieldName();
            var id = new Identifier(fieldName.getText(), rangeFrom(fieldName.ID()));
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
            var importStr = useCtx.QUALIFIED_NAME().getText();
            int idx = importStr.lastIndexOf('/');
            var name = importStr.substring(idx + 1);
            if (useCtx.FOREIGN() != null) {
              var use = new Use.Foreign(importStr, name, rangeFrom(useCtx));
              scope.addUse(use);
              useList.add(use);
            }
          }
      }

      return switch (structKind) {
        case LITERAL -> Struct.literalStruct(fields, functions, rangeFrom(ctx));
        case MODULE -> Struct.moduleStruct(name, useList, fields, functions, rangeFrom(ctx));
      };
    }

  }

  static class FunctionSignatureVisitor extends SasquachBaseVisitor<FunctionSignature> {
    @Override
    public FunctionSignature visitFunctionDeclaration(
        FunctionDeclarationContext ctx) {
      var typeVisitor = new TypeVisitor();

      List<FunctionArgumentContext> paramsCtx = ctx.functionArgument();
      var params = new ArrayList<FunctionParameter>();
      for (FunctionArgumentContext paramCtx : paramsCtx) {
        var type = paramCtx.type().accept(typeVisitor);
        var id = new Identifier(paramCtx.ID().getText(), rangeFrom(paramCtx.ID()));
        var param = new FunctionParameter(id, type, (Range.Single) rangeFrom(paramCtx.type()));
        params.add(param);
      }

      return new FunctionSignature(params, ctx.type().accept(typeVisitor), rangeFrom(ctx));
    }
  }
}
