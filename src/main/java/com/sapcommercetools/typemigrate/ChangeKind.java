package com.sapcommercetools.typemigrate;

/**
 * The kind of schema change detected between two {@link TypeModel}s.
 */
public enum ChangeKind {
    /** A type exists in the target model but not in the current one. */
    ADD_TYPE,
    /** A type exists in the current model but not in the target one. */
    REMOVE_TYPE,
    /** An attribute was added to an existing type. */
    ADD_ATTRIBUTE,
    /** An attribute was removed from an existing type. */
    REMOVE_ATTRIBUTE,
    /** An attribute's declared type changed. */
    CHANGE_ATTRIBUTE_TYPE
}
