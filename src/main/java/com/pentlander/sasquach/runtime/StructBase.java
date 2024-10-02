package com.pentlander.sasquach.runtime;

import java.lang.constant.ClassDesc;

/**
 * Runtime base type for structs.
 * <p>Having a tag interface for all structs simplifies the method dispatch.</p>
 */
public interface StructBase {
  ClassDesc CD = StructBase.class.describeConstable().orElseThrow();
}
