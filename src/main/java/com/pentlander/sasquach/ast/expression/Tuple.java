package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import java.util.ArrayList;
import java.util.List;

public record Tuple(List<Field> fields,  Range range) implements Struct {
  public static final QualifiedModuleName TUPLE_MODULE = new QualifiedModuleName(new PackageName("std/tuple"), "Tuple");

  public static Tuple of(List<Expression> expressions, Range range) {
    var fields = new ArrayList<Field>(expressions.size());
    for (int i = 0; i < expressions.size(); i++) {
      var expr = expressions.get(i);
      fields.add(new Field(new Id("_" + i, (Single) expr.range()), expr));
    }
    return new Tuple(List.copyOf(fields), range);
  }

  public static QualifiedTypeName tupleName(int arity) {
    return TUPLE_MODULE.qualifyInner(new UnqualifiedTypeName(Integer.toString(arity)));
  }

  @Override
  public List<NamedFunction> functions() {
    return List.of();
  }
}
