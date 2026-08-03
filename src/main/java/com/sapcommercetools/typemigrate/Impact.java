package com.sapcommercetools.typemigrate;

/**
 * The operational risk of applying a {@link SchemaChange}.
 */
public enum Impact {
    /** Purely additive; applying it cannot lose data (e.g. add type/attribute). */
    SAFE,
    /** Needs human review; may need data conversion (e.g. attribute type change). */
    REVIEW,
    /** Data-losing; dropping a type or attribute discards persisted values. */
    DESTRUCTIVE
}
