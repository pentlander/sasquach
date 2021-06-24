package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.Block;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.Expression;
import com.pentlander.sasquach.ast.Function;

import com.pentlander.sasquach.ast.FunctionCall;
import com.pentlander.sasquach.ast.FunctionParameter;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.VariableDeclaration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class AstValidator {
  private final CompilationUnit compilationUnit;
  private final Source source;

  public AstValidator(CompilationUnit compilationUnit, Source source) {
    this.compilationUnit = compilationUnit;
    this.source = source;
  }

  List<Error> validate() {
    var errors = new ArrayList<Error>();
    ModuleDeclaration module = compilationUnit.module();
    List<Function> functions = module.struct().functions();
    var functionNames = new HashMap<String, Function>();

    for (Function function : functions) {
      Function existingFunction = functionNames.put(function.name(), function);
      // Check if there are multiple functions with the same name
      if (existingFunction != null) {
        errors.add(new DuplicationError(
            "Function '%s' already defined in module '%s'".formatted(function.name(), module.name()),
            List.of(
                existingFunction.nameRange(),
                function.nameRange())));
      }
      // Check that func return expression matches the return type of the function
      if (function.returnExpression() != null
          && !function.returnType().equals(function.returnExpression().type())) {
        errors.add(
            new TypeMismatchError(
                "Return value of type '%s' does not match function return type '%s'"
                    .formatted(
                        exprTypeName(function.returnExpression()),
                        function.returnType().typeName()), function.returnExpression().range()));
      }

      errors.addAll(validateFunctionParameters(function.name(), function.parameters()));
      errors.addAll(validateExpression(function.expression()));
    }

    return errors;
  }

  private List<Error> validateExpression(Expression expression) {
    var errors = new ArrayList<Error>();
    if (expression instanceof Block block) {
      errors.addAll(validateBlock(block));
    }

    return errors;
  }

  private List<Error> validateBlock(Block block) {
    var errors = new ArrayList<Error>();
    var variables = new HashMap<String, List<VariableDeclaration>>();
    for (var expr : block.expressions()) {
      if (expr instanceof FunctionCall funcCall) {
        var params = funcCall.signature().parameters();
        if (funcCall.arguments().size() != params.size()) {
          errors.add(new BasicError(
              "Function '%s' expects %s arguments but found %s\n%s\nnote: function defined here\n%s".formatted(
                  funcCall.name(),
                  params.size(),
                  funcCall.arguments().size(),
                  source.highlight(funcCall.range()),
                  source.highlight(funcCall.signature().range()))));
        } else {
          for (int i = 0; i < params.size(); i++) {
            var arg = funcCall.arguments().get(i);
            var param = params.get(i);
            if (!arg.type().equals(param.type())) {
              errors.add(new TypeMismatchError("Expected arg of type '%s', but found type '%s'".formatted(param.type().typeName(),
                      exprTypeName(arg)), arg.range()));
            }
          }
        }
      } else if (expr instanceof VariableDeclaration varDecl) {
        variables.computeIfAbsent(varDecl.name(), k -> new ArrayList<>()).add(varDecl);
      }
    }

    variables.forEach((name, vars) -> {
      if (vars.size() > 1) {
        errors.add(new DuplicationError(
            "Variable '%s' already defined in block".formatted(name), vars.stream().map(VariableDeclaration::nameRange).toList()));
      }
    });

    return errors;
  }

  record SizeMismatchError(String message, Range range) implements Error {
    @Override
    public String toPrettyString(Source source) {
      return message + "\n" + source.highlight(null);
    }
  }

  private List<Error> validateFunctionParameters(String funcName, List<FunctionParameter> funcParameters) {
    var paramNames =
        funcParameters.stream().collect(Collectors.groupingBy(FunctionParameter::name));
    var errors = new ArrayList<Error>();
    paramNames.forEach((name, params) -> {
      if (params.size() > 1) {
        errors.add(new DuplicationError("Parameter '%s' already defined in function '%s'".formatted(name, funcName),
                params.stream().map(param -> param.id().range()).toList()));
      }
    });

    return errors;
  }

  private String exprTypeName(Expression expression) {
    return expression.type().typeName();
  }

  record TypeMismatchError(String message, Range range) implements Error {
    @Override
    public String toPrettyString(Source source) {
      return """
          %s
          %s
          """.formatted(message, source.highlight(range));
    }
  }

  record DuplicationError(String message, List<Range.Single> ranges) implements Error {
    @Override
    public String toPrettyString(Source source) {
      var firstRange = ranges.get(0);
      var restHighlights = ranges.stream()
          .skip(1)
          .map(range -> source.highlight(range) + " other appearance here")
          .collect(Collectors.joining("\n"));
      return  """
         %s
         %s first appearance here
         %s
         """.formatted(message, source.highlight(firstRange), restHighlights);
    }
  }
}
