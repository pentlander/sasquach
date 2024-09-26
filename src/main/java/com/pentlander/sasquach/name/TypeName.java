package com.pentlander.sasquach.name;

sealed public interface TypeName extends Name permits QualifiedTypeName, UnqualifiedTypeName {}
