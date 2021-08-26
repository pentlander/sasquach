package com.pentlander.sasquach.name;

import java.util.List;

public record ForeignFunctions(Class<?> ownerClass, List<ForeignFunctionHandle> functions) {}
