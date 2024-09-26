package com.pentlander.sasquach.ast.id;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.name.UnqualifiedTypeName;

public record TypeParameterId(UnqualifiedTypeName name, Range.Single range) implements TypeIdentifier {}
