package com.sapcommercetools.typemigrate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable snapshot of a single item type in the type system.
 *
 * <p>{@link #attributes()} maps each attribute name to its declared type
 * (e.g. {@code "code" -> "java.lang.String"}). The map is defensively copied
 * and unmodifiable.
 */
public final class ItemType {

    private final String code;
    private final Map<String, String> attributes;

    /**
     * @param code       the item type code (must not be {@code null})
     * @param attributes attribute-name → attribute-type map (copied; may be empty)
     * @throws NullPointerException if {@code code} is null
     */
    public ItemType(String code, Map<String, String> attributes) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.attributes = attributes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** @return the item type code. */
    public String code() {
        return code;
    }

    /** @return an unmodifiable view of attribute-name → attribute-type. */
    public Map<String, String> attributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemType other)) {
            return false;
        }
        return code.equals(other.code) && attributes.equals(other.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, attributes);
    }

    @Override
    public String toString() {
        return "ItemType[" + code + ", attributes=" + attributes + "]";
    }
}
