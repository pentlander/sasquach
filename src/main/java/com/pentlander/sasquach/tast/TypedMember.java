package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.tast.expression.TStruct.TField;

public sealed interface TypedMember permits TNamedFunction, TField {}
