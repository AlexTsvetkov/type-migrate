package com.sapcommercetools.typemigrate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable snapshot of a whole type system: item type code → {@link ItemType}.
 *
 * <p>Conceptually a {@code Map<String, ItemType>}; this wrapper adds a small
 * fluent builder and defensive copying so a model cannot be mutated after a
 * diff has been taken.
 */
public final class TypeModel {

    private final Map<String, ItemType> types;

    private TypeModel(Map<String, ItemType> types) {
        this.types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
    }

    /**
     * Wraps an existing map of types.
     *
     * @param types code → item type (copied; may be empty, not {@code null})
     * @return a new immutable model
     */
    public static TypeModel of(Map<String, ItemType> types) {
        return new TypeModel(Objects.requireNonNull(types, "types must not be null"));
    }

    /** @return an empty model. */
    public static TypeModel empty() {
        return new TypeModel(Collections.emptyMap());
    }

    /** @return a fresh {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /** @return an unmodifiable view of code → item type. */
    public Map<String, ItemType> types() {
        return types;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TypeModel other && types.equals(other.types);
    }

    @Override
    public int hashCode() {
        return types.hashCode();
    }

    @Override
    public String toString() {
        return "TypeModel" + types.keySet();
    }

    /** Fluent builder for a {@link TypeModel}. */
    public static final class Builder {
        private final Map<String, ItemType> types = new LinkedHashMap<>();

        /**
         * Adds a type with the given attributes.
         *
         * @param code       the item type code
         * @param attributes attribute-name → attribute-type
         * @return this builder
         */
        public Builder type(String code, Map<String, String> attributes) {
            types.put(code, new ItemType(code, attributes));
            return this;
        }

        /**
         * Adds a pre-built type.
         *
         * @param itemType the type to add
         * @return this builder
         */
        public Builder type(ItemType itemType) {
            types.put(itemType.code(), itemType);
            return this;
        }

        /** @return the immutable model. */
        public TypeModel build() {
            return new TypeModel(types);
        }
    }
}
