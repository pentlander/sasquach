package com.pentlander.sasquach.rdparser;

import static com.pentlander.sasquach.ast.expression.Struct.tupleStruct;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.Branch;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.Pattern;
import com.pentlander.sasquach.ast.Pattern.Singleton;
import com.pentlander.sasquach.ast.PatternVariable;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.Use.Foreign;
import com.pentlander.sasquach.ast.Use.Module;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathOperator;
import com.pentlander.sasquach.ast.expression.*;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.id.QualifiedModuleId;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.ast.id.TypeIdentifier;
import com.pentlander.sasquach.ast.id.TypeParameterId;
import com.pentlander.sasquach.ast.typenode.ArrayTypeNode;
import com.pentlander.sasquach.ast.typenode.BasicTypeNode;
import com.pentlander.sasquach.ast.typenode.FunctionSignature;
import com.pentlander.sasquach.ast.typenode.NamedTypeNode;
import com.pentlander.sasquach.ast.typenode.StructTypeNode;
import com.pentlander.sasquach.ast.typenode.StructTypeNode.RowModifier;
import com.pentlander.sasquach.ast.typenode.SumTypeNode;
import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode.SingletonTypeNode;
import com.pentlander.sasquach.ast.typenode.TupleTypeNode;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.ast.typenode.TypeStatement;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import com.pentlander.sasquach.nameres.NameNotFoundError;
import com.pentlander.sasquach.parser.CompileResult;
import com.pentlander.sasquach.parser.ModuleContext;
import com.pentlander.sasquach.parser.StructIdentifier;
import com.pentlander.sasquach.parser.StructIdentifier.ModuleName;
import com.pentlander.sasquach.parser.StructIdentifier.None;
import com.pentlander.sasquach.rdparser.Parser.Tree;
import com.pentlander.sasquach.rdparser.Parser.TreeKind;
import com.pentlander.sasquach.rdparser.Parser.TreeReader;
import com.pentlander.sasquach.rdparser.Scanner.Token;
import com.pentlander.sasquach.rdparser.Scanner.TokenType;
import com.pentlander.sasquach.type.ArrayType;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.TypeParameterNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class AstBuilder {
  private final ModuleContext moduleCtx;

  public AstBuilder(ModuleContext moduleCtx) {
    this.moduleCtx = moduleCtx;
  }

  static CompileResult<CompilationUnit> build(PackageName packageName, SourcePath sourcePath, Tree tree) {
    var treeReader = tree.read();
    var cutr = treeReader.assertTree(TreeKind.COMP_UNIT);
    var errors = RangedErrorList.builder();
    var modDecls = cutr.filterChildren(TreeKind.MODULE)
        .map(mtr -> {
          var moduleId = expectId(mtr);
          var qualId = new QualifiedModuleId(packageName, moduleId.name().toString(), moduleId.range());
          var ast = new AstBuilder(new ModuleContext(qualId.moduleName(), sourcePath));

          var result = ast.expectModDecl(qualId, mtr);
          errors.addAll(ast.moduleCtx.errors());
          return result.item();
        }).toList();
    return CompileResult.of(new CompilationUnit(sourcePath, packageName, modDecls), errors.build());
  }

  private CompileResult<ModuleDeclaration> expectModDecl(QualifiedModuleId qualifiedId, TreeReader mtr) {
    var struct = (ModuleStruct) expectStruct(new ModuleName(qualifiedId.name()), mtr);
    var modDecl = new ModuleDeclaration(qualifiedId, struct, mtr.range());
    return CompileResult.of(modDecl, moduleCtx.errors());
  }

  private static IllegalStateException illegalTokenType(Token token) {
    throw new IllegalStateException("Unexpected token type: " + token.type());
  }

  private static IllegalStateException illegalTreeKind(TreeReader tree) {
    throw new IllegalStateException("Unexpected tree kind: " + tree.treeKind());
  }

  private VariableDeclaration assertVarDecl(TreeReader treeReader) {
    var tr = treeReader.assertTree(TreeKind.VAR_DECL);
    tr.expectToken(TokenType.LET);
    var id = expectId(tr);
    var typeAnn = eatTypeAnnotation(tr, List.of());
    tr.expectToken(TokenType.EQ);
    var expr = expectExpr(tr);
    return new VariableDeclaration(id, typeAnn, expr, tr.range());
  }

  private Pattern expectPattern(TreeReader treeReader) {
    var tr = treeReader.expectTree(TreeKind.PATTERN);
    var namedTypeNode = (NamedTypeNode) assertNamedTypeNode(tr.expectTree(), List.of());
    var typeId = (TypeId) namedTypeNode.id();
    if (tr.eatToken(TokenType.L_PAREN) != null) {
      var patternVars = tr.filterChildren(TokenType.NAME)
          .map(AstBuilder::id)
          .map(PatternVariable::new)
          .toList();
      return new Pattern.VariantTuple(typeId, patternVars, tr.range());
    } else if (tr.eatToken(TokenType.L_CURLY) != null) {
      var patternVars = tr.filterChildren(TokenType.NAME)
          .map(AstBuilder::id)
          .map(PatternVariable::new)
          .toList();
      return new Pattern.VariantStruct(typeId, patternVars, tr.range());
    } else {
      return new Singleton(typeId);
    }
  }

  private Expression expectExpr(TreeReader treeReader) {
    return assertExpr(treeReader.expectTree());
  }

  private Expression assertExpr(TreeReader tr) {
    return switch (tr.treeKind()) {
      case TreeKind.EXPR_VAR_REF -> new VarReference(expectId(tr));
      case TreeKind.EXPR_PAREN -> {
        tr.expectToken(TokenType.L_PAREN);
        yield expectExpr(tr);
      }
      case TreeKind.EXPR_NOT -> {
        tr.expectToken(TokenType.BANG);
        yield new Not(expectExpr(tr), tr.range());
      }
      case TreeKind.EXPR_BIN_MATH -> {
        var leftExpr = expectExpr(tr);
        var token = tr.expectToken();
        var rightExpr = expectExpr(tr);

        var op = MathOperator.fromString(token.lexeme());
        yield new MathExpression(op, leftExpr, rightExpr, tr.range());
      }
      case TreeKind.EXPR_BIN_COMPARE -> {
        var leftExpr = expectExpr(tr);
        var token = tr.expectToken();
        var rightExpr = expectExpr(tr);

        var op = CompareOperator.fromString(token.lexeme());
        yield new CompareExpression(op, leftExpr, rightExpr, tr.range());
      }
      case TreeKind.EXPR_BIN_BOOLEAN -> {
        var leftExpr = expectExpr(tr);
        var token = tr.expectToken();
        var rightExpr = expectExpr(tr);

        var op = BooleanOperator.fromString(token.lexeme());
        yield new BooleanExpression(op, leftExpr, rightExpr, tr.range());
      }
      case TreeKind.EXPR_PIPE -> {
        var expr = expectExpr(tr);
        tr.expectToken(TokenType.PIPE_OP);
        var funcExpr2 = expectExpr(tr);
        var funcExpr = (FunctionCall) funcExpr2;
        yield new PipeOperator(expr, funcExpr, tr.range());
      }
      case TreeKind.EXPR_APPLY -> {
        var expr = expectExpr(tr);
        tr.expectToken(TokenType.L_PAREN);
        var args = tr.filterChildrenTrees()
            // TODO will need to be changed to work with labeled args
            .map(etr -> new Argument(assertExpr(etr)))
            .toList();
        yield switch (expr) {
          case VarReference(Id id) -> {
            if (id.name().toString().equals("recur")) {
              yield new Recur(args, tr.range());
            }
            yield new LocalFunctionCall(id, args, tr.range());
          }
          case MemberAccess(Expression structExpr, Id id) ->
              new MemberFunctionCall(structExpr, id, args, tr.range());
          case ForeignFieldAccess(TypeId typeId, Id id) ->
              new ForeignFunctionCall(typeId, id, args, tr.range());
          default -> throw illegalTreeKind(tr);
        };
      }
      case TreeKind.EXPR_IF -> {
        tr.expectToken(TokenType.IF);
        var ifCond = expectExpr(tr);
        var trueExpr = expectExpr(tr);
        Expression falseExpr = null;
        if (tr.eatToken(TokenType.ELSE) != null) {
          falseExpr = expectExpr(tr);
        }
        yield new IfExpression(ifCond, trueExpr, falseExpr, tr.range());
      }
      case TreeKind.EXPR_STRUCT -> assertStruct(StructIdentifier.NONE, tr);
      case TreeKind.EXPR_NAMED_STRUCT -> {
        var namedType = (NamedTypeNode) assertNamedTypeNode(tr.expectTree(), List.of());
        yield expectStruct(new StructIdentifier.TypeNode(namedType), tr);
      }
      case TreeKind.EXPR_MATCH -> {
        tr.expectToken(TokenType.MATCH);
        var expr = expectExpr(tr);
        var branches = tr.filterChildren(TreeKind.MATCH_BRANCH)
            .map(btr -> {
              var pattern = expectPattern(btr);
              btr.expectToken(TokenType.ARROW);
              var branchExpr = expectExpr(btr);
              return new Branch(pattern, branchExpr, btr.range());
            }).toList();
        yield new Match(expr, branches, tr.range());
      }
      case TreeKind.EXPR_FUNC -> assertFunction(tr);
      case TreeKind.EXPR_LITERAL -> {
        var token = tr.expectToken();
        var type = switch (token.type()) {
          case TRUE, FALSE -> BuiltinType.BOOLEAN;
          case INT_LIKE -> BuiltinType.INT;
          case DOUBLE_LIKE -> BuiltinType.DOUBLE;
          case STRING -> BuiltinType.STRING;
          default -> throw illegalTokenType(token);
        };
        yield new Value(type, requireNonNullElse(token.literal(), token.lexeme()), token.range());
      }
      case TreeKind.EXPR_MEMBER_ACCESS -> {
        var expr = expectExpr(tr);
        tr.expectToken(TokenType.DOT);
        var memberExpr = expectExpr(tr);
        if (memberExpr instanceof VarReference(Id memberId)) {
          yield new MemberAccess(expr, memberId);
        }
        throw illegalTreeKind(tr);
      }
      case TreeKind.EXPR_LOOP -> {
        tr.expectToken(TokenType.LOOP);
        tr.expectToken(TokenType.L_PAREN);
        var lvdtr = tr.expectTree(TreeKind.LOOP_VAR_DECLS);
        var varDecls = lvdtr.filterChildren(TreeKind.VAR_DECL)
            .map(this::assertVarDecl)
            .toList();
        tr.expectToken(TokenType.ARROW);
        yield new Loop(varDecls, expectExpr(tr), tr.range());
      }
      case TreeKind.EXPR_BLOCK -> {
        tr.expectToken(TokenType.L_CURLY);
        //
        if (tr.nextIs(TokenType.R_CURLY)) {
          tr.advance();
          yield new Value(BuiltinType.VOID, "", tr.range());
        }

        var exprs = tr.filterChildrenTrees()
            .map(sttr -> switch (sttr.treeKind()) {
              case VAR_DECL -> assertVarDecl(sttr);
              case BLOCK_PRINT_STMT -> {
                sttr.expectToken(TokenType.PRINT);
                yield new PrintStatement(expectExpr(sttr), sttr.range());
              }
              case BLOCK_EXPR -> expectExpr(sttr);
              default -> throw illegalTreeKind(sttr);
            }).toList();
        yield new Block(exprs, tr.range());
      }
      case TreeKind.EXPR_TUPLE -> {
        tr.expectToken(TokenType.L_PAREN);
        var exprs = new ArrayList<Expression>();
        exprs.add(expectExpr(tr));
        tr.expectToken(TokenType.COMMA);
        while (tr.hasRemaining()) {
          if (tr.nextIs(TokenType.R_PAREN)) break;
          exprs.add(expectExpr(tr));
          tr.eatToken(TokenType.COMMA);
        }
        yield tupleStruct(exprs, tr.range());
      }
      case TreeKind.EXPR_FOREIGN_ACCESS -> {
        var typeId = expectForiegnTypeId(tr);
        tr.expectToken(TokenType.POUND);
        var memberId = expectId(tr);
        yield new ForeignFieldAccess(typeId, memberId);
      }
      default -> throw illegalTreeKind(tr);
    };
  }

  private Struct expectStruct(StructIdentifier structName, TreeReader treeReader) {
    var tr = treeReader.expectTree(TreeKind.EXPR_STRUCT);
    return assertStruct(structName, tr);
  }

  private Struct assertStruct(StructIdentifier structName, TreeReader tr) {
    var useTrs = new ArrayList<TreeReader>();
    var typeTrs = new ArrayList<TreeReader>();
    var memberTrs = new ArrayList<TreeReader>();
    var spreadTrs = new ArrayList<TreeReader>();
    tr.assertTree(TreeKind.EXPR_STRUCT)
        .filterChildren(TreeKind.STRUCT_STATEMENT)
        .forEach(sstr -> {
          switch (sstr.peekToken().type()) {
            case USE -> useTrs.add(sstr);
            case TYPE, TYPEALIAS -> typeTrs.add(sstr);
            case NAME -> memberTrs.add(sstr);
            case DOT_DOT -> spreadTrs.add(sstr);
          }
        });

    var useList = new ArrayList<Use>();
    for (var useTr : useTrs) {
      useTr.expectToken();
      boolean isForeign = useTr.eatToken(TokenType.FOREIGN) != null;
      var qualNameTr = useTr.expectTree(TreeKind.QUALIFIED_NAME);
      var names = qualNameTr
          .filterChildren(TokenType.NAME)
          .toList();
      var packageName = new PackageName(names.subList(0, names.size() - 1)
          .stream()
          .map(Token::lexeme)
          .collect(joining("/")));
      // Splits literally on both chars
      var aliasToken = names.getLast();
      var aliasParts = aliasToken.lexeme().split("[$.]");
      var aliasId = new Id(aliasParts[aliasParts.length - 1], aliasToken.singleRange());
      var qualifiedId = new QualifiedModuleId(packageName, aliasToken.lexeme(),
          qualNameTr.singleRange());
      Use use;
      if (isForeign) {
        use = new Foreign(qualifiedId, aliasId, useTr.range());
        moduleCtx.putTypeName(aliasId.name().toTypeName(), qualifiedId.name().toQualifiedTypeName());
      } else {
        use = new Module(qualifiedId, aliasId, useTr.range());
        moduleCtx.putModuleName(aliasId.name(), qualifiedId.moduleName());
      }
      useList.add(use);
    }

    for (var origTypeTr : typeTrs) {
      var ttr = origTypeTr.copy();
      ttr.expectToken();
      var name = typeName(ttr.expectToken(TokenType.NAME));
      var moduleName = (ModuleName) structName;
      moduleCtx.putTypeName(name, moduleName.name().qualifyInner(name));
    }

    var typeStatements = new ArrayList<TypeStatement>();
    for (var ttr : typeTrs) {
      var isAlias = ttr.expectToken().hasType(TokenType.TYPEALIAS);
      var aliasId = expectTypeId(ttr);
      var typeParams = eatTypeParams(ttr);
      var moduleName = ((ModuleName) structName).name();
      ttr.expectToken(TokenType.EQ);
      var tdtr = ttr.expectTree();
      var typeNode = switch (tdtr.treeKind()) {
        case TreeKind.TYPE_EXPR -> assertTypeNode(tdtr, aliasId.name(), typeParams);
        case TreeKind.SUM_TYPEDEF -> {
          var variantTypeNodes = tdtr.filterChildren(TreeKind.VARIANT_TYPE_STRUCT,
              TreeKind.VARIANT_TYPE_TUPLE,
              TreeKind.VARIANT_TYPE_SINGLETON).map(vttr -> {
                vttr.expectToken(TokenType.PIPE);
                var token = vttr.expectToken(TokenType.NAME);
                var name = moduleName.qualifyInner(typeName(token));
                var typeId = new TypeId(name, token.singleRange());
                moduleCtx.putTypeName(name.simpleName(), name);
                return switch (vttr.treeKind()) {
                  case TreeKind.VARIANT_TYPE_STRUCT -> (StructTypeNode) assertTypeNode(vttr.expectTree(), name,
                      typeParams);
                  case TreeKind.VARIANT_TYPE_TUPLE -> (TupleTypeNode) assertTypeNode(vttr.expectTree(), name,
                      typeParams);
                  case TreeKind.VARIANT_TYPE_SINGLETON -> new SingletonTypeNode(typeId);
                  default -> throw new IllegalStateException();
                };
          }).toList();

          yield new SumTypeNode(moduleName, aliasId, typeParams, variantTypeNodes, ttr.range());
        }
        default -> throw new IllegalStateException();
      };
      typeStatements.add(new TypeStatement(aliasId, typeParams, typeNode, isAlias, ttr.range()));
    }

    boolean isModule = structName instanceof ModuleName;
    var fields = new ArrayList<Field>();
    var functions = new ArrayList<NamedFunction>();
    for (var mtr : memberTrs) {
      var id = expectId(mtr);
      mtr.expectToken(TokenType.EQ);
      var expr = expectExpr(mtr);
      if (isModule && expr instanceof Function func) {
        functions.add(new NamedFunction(id, func));
      } else {
        fields.add(new Field(id, expr));
      }
    }

    var spreads = new ArrayList<VarReference>();
    for (var sstr : spreadTrs) {
      sstr.expectToken(TokenType.DOT_DOT);
      spreads.add((VarReference) expectExpr(sstr));
    }

    var range = tr.range();
    return switch (structName) {
      case None _ -> Struct.literalStruct(fields, functions, spreads, range);
      case ModuleName(var name) -> Struct.moduleStructBuilder(name)
          .useList(useList)
          .typeStatements(typeStatements)
          .fields(fields)
          .functions(functions)
          .range(range)
          .build();
      case StructIdentifier.TypeNode(var node) ->
          Struct.namedStructConstructor((QualifiedTypeName) node.id().name(), fields, range);
    };
  }

  private Function assertFunction(TreeReader ftr) {
    ftr.assertTree(TreeKind.EXPR_FUNC);
    var typeParams = eatTypeParams(ftr);
    var funcParams = expectFunctionParameterList(ftr, typeParams);
    var returnType =
        ftr.eatToken(TokenType.COLON) != null ? assertTypeNode(
            ftr.expectTree(TreeKind.TYPE_EXPR),
            typeParams) : null;
    var funcSig = new FunctionSignature(funcParams, typeParams, returnType, ftr.range());
    ftr.expectToken(TokenType.ARROW);

    return new Function(funcSig, expectExpr(ftr));
  }

  List<TypeNode> eatTypeArguments(TreeReader tr, List<TypeParameterNode> typeParams) {
    var tatr = tr.eatTree(TreeKind.TYPE_ARG_LIST);
    return tatr != null ? tatr.filterChildren(TreeKind.TYPE_EXPR).map(treeReader -> assertTypeNode(
        treeReader, typeParams)).toList()
        : List.of();
  }

  private TypeNode assertNamedTypeNode(TreeReader ttr, List<TypeParameterNode> typeParams) {
    ttr.assertTree(TreeKind.NAMED_TYPE);
    var firstTypeNameToken = ttr.expectToken(TokenType.NAME);
    ttr.eatToken(TokenType.DOT);
    var secondTypeNameToken = ttr.eatToken(TokenType.NAME);
    TypeIdentifier typeId;
    // If the name is not qualified with a module, resolve the name as either a builtin type or
    // a local type
    if (secondTypeNameToken == null) {
      var name = firstTypeNameToken.lexeme();
      // Check if builtin or array type
      var builtin = BuiltinType.fromStringOpt(name);
      if (builtin.isPresent()) {
        // TODO need to validate that there aren't any type args
        return new BasicTypeNode(builtin.get(), firstTypeNameToken.singleRange());
      } else if (name.equals(ArrayType.TYPE_NAME)) {
        var typeArgs = eatTypeArguments(ttr, typeParams);
        return new ArrayTypeNode(typeArgs.getFirst(), ttr.range());
      }

      // Otherwise it's a local named type
      var typeName = new UnqualifiedTypeName(name);
      var qualName = moduleCtx.getTypeName(typeName);
      var range = firstTypeNameToken.singleRange();
      // If the name was found in the module context, then it's a locally defined type from a
      // typedef or type alias
      if (qualName != null) {
        typeId = new TypeId(qualName, range);
      } else {
        // Otherwise try looking in the type parameter names as this type could be nested in
        // a function with type parameters
        if (typeParams.stream().anyMatch(param -> param.name().equals(typeName))) {
          typeId = new TypeParameterId(typeName, range);
          // If the name still cannot be resolved, return an error
        } else {
          var fakeName = moduleCtx.moduleName().qualifyInner(typeName);
          typeId = new TypeId(fakeName, range);
          moduleCtx.addError(new NameNotFoundError(typeId, "type"));
        }
      }
    } else {
      var moduleId = id(firstTypeNameToken);
      var qualModuleName = moduleCtx.getModuleName(moduleId);
      var typeName = typeName(secondTypeNameToken);
      typeId = new TypeId(new QualifiedTypeName(qualModuleName, typeName), ttr.singleRange());
    }
    return new NamedTypeNode(typeId, eatTypeArguments(ttr, typeParams), ttr.range());
  }

  TypeNode assertTypeNode(TreeReader treeReader, List<TypeParameterNode> typeParams) {
    return assertTypeNode(treeReader, null, typeParams);
  }

  TypeNode assertTypeNode(TreeReader treeReader,
      @Nullable QualifiedTypeName structName, List<TypeParameterNode> typeParams) {
    var ttr = treeReader.assertTree(TreeKind.TYPE_EXPR).expectTree();
    return switch (ttr.treeKind()) {
      case TreeKind.NAMED_TYPE -> assertNamedTypeNode(ttr, typeParams);
      case TreeKind.STRUCT_TYPE -> {
        var fields = new LinkedHashMap<UnqualifiedName, TypeNode>();
        RowModifier rowModifier = RowModifier.none();
        for (var it = ttr.filterChildren(TreeKind.STRUCT_TYPE_MEMBER, TreeKind.STRUCT_TYPE_SPREAD).iterator(); it.hasNext();) {
          var sttr = it.next();
          switch (sttr.treeKind()) {
            case TreeKind.STRUCT_TYPE_MEMBER -> {
              var name = expectName(sttr);
              var typeNode = requireNonNull(eatTypeAnnotation(sttr, typeParams));
              fields.put(name, typeNode);
            }
            case TreeKind.STRUCT_TYPE_SPREAD -> {
              sttr.expectToken(TokenType.DOT_DOT);
              var namedType = sttr.eatTree(TreeKind.NAMED_TYPE);
              if (namedType != null) {
                var namedTypeNode = (NamedTypeNode) assertNamedTypeNode(namedType, typeParams);
                rowModifier = RowModifier.namedRow(namedTypeNode.id(), namedTypeNode.range());
              } else {
                rowModifier = RowModifier.unnamedRow();
              }
            }
          }
        }
        // Not sure the typeParams field should be passed in here
        yield new StructTypeNode(structName, typeParams, fields, rowModifier, ttr.range());
      }
      case TreeKind.TUPLE_TYPE -> {
        var typeNodes = ttr.filterChildren(TreeKind.TYPE_EXPR)
            .map(tetr -> assertTypeNode(tetr, typeParams))
            .toList();
        var name = requireNonNullElseGet(structName, () -> Tuple.tupleName(typeNodes.size()));
        yield new TupleTypeNode(name, typeNodes, ttr.range());
      }
      case TreeKind.FUNCTION_TYPE -> {
        var funcParams = expectFunctionParameterList(ttr, typeParams);
        ttr.expectToken(TokenType.ARROW);
        var returnType = assertTypeNode(ttr.expectTree(TreeKind.TYPE_EXPR), typeParams);
        yield new FunctionSignature(funcParams, returnType, ttr.range());
      }
      default -> throw new IllegalStateException();
    };
  }

  private List<FunctionParameter> expectFunctionParameterList(TreeReader tr,
      List<TypeParameterNode> typeParams) {
    return tr.expectTree(TreeKind.FUNCTION_PARAM_LIST)
        .filterChildren(TreeKind.FUNCTION_PARAM)
        .map(fptr -> {
          var id = expectId(fptr);
          Id label = null;
          var nameToken = fptr.eatToken(TokenType.NAME);
          if (nameToken != null) {
            label = id;
            id = id(nameToken);
          }
          var typeNode = eatTypeAnnotation(fptr, typeParams);
          return new FunctionParameter(id, label, typeNode, null);
        })
        .toList();
  }

  @Nullable
  private TypeNode eatTypeAnnotation(TreeReader treeReader, List<TypeParameterNode> typeParams) {
    var tatr = treeReader.eatTree(TreeKind.TYPE_ANNOTATION);
    if (tatr != null) {
      tatr.expectToken(TokenType.COLON);
      return assertTypeNode(tatr.expectTree(TreeKind.TYPE_EXPR), typeParams);
    }
    return null;
  }

  private List<TypeParameterNode> eatTypeParams(TreeReader tr) {
    var tpltr = tr.eatTree(TreeKind.TYPE_PARAM_LIST);
    if (tpltr == null) {
      return List.of();
    }

    return tpltr.filterChildren(TokenType.NAME).map(nameToken -> {
      var typeName = typeName(nameToken);
      return new TypeParameterNode(new TypeParameterId(typeName, nameToken.singleRange()));
    }).toList();
  }

  private static Id id(Token token) {
    return new Id(token.lexeme(), token.singleRange());
  }

  private static UnqualifiedName name(Token token) {
    return new UnqualifiedName(token.lexeme());
  }

  private static UnqualifiedTypeName typeName(Token token) {
    return new UnqualifiedTypeName(token.lexeme());
  }

  private static Id expectId(TreeReader tr) {
    var token = tr.expectToken(TokenType.NAME);
    return id(token);
  }

  private TypeId expectTypeId(TreeReader treeReader) {
    var token = treeReader.expectToken(TokenType.NAME);
    var name = typeName(token);
    if (BuiltinType.fromStringOpt(name.toString()).isPresent()) {
      throw new IllegalStateException("Type name cannot be a builtin type: " + name);
    }
    var qualName = requireNonNull(moduleCtx.getTypeName(name), name.toString());
    // TODO This is the wrong range, it's the range of the parent tree rather than just the type id
    return new TypeId(qualName, token.singleRange());
  }

  private TypeId expectForiegnTypeId(TreeReader treeReader) {
    var token = treeReader.expectToken(TokenType.NAME);
    var name = typeName(token);
    var qualName = moduleCtx.getTypeName(name);
    if (qualName == null) {
      moduleCtx.addError(new NameNotFoundError(name, token.range(), "foreign class", List.of()));
      qualName = new QualifiedTypeName(QualifiedModuleName.EMPTY, name);
    }
    // TODO This is the wrong range, it's the range of the parent tree rather than just the type id
    return new TypeId(qualName, token.singleRange());
  }

  private static UnqualifiedName expectName(TreeReader tr) {
    return new UnqualifiedName(tr.expectToken(TokenType.NAME).lexeme());
  }

  private QualifiedModuleId qualifiedModuleId(TreeReader tr) {
    var names = tr.assertTree(TreeKind.QUALIFIED_NAME)
        .filterChildren(TokenType.NAME)
        .map(Token::lexeme)
        .toList();
    var packageName = new PackageName(String.join("/", names.subList(0, names.size() - 1)));
    var qualName = new QualifiedModuleName(packageName, names.getLast());
    return new QualifiedModuleId(qualName,  tr.singleRange());
  }
}
