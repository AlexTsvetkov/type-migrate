package com.sapcommercetools.typemigrate;

/**
 * A single, atomic difference between two {@link TypeModel}s.
 *
 * @param kind      the kind of change
 * @param typeCode  the affected item type code
 * @param attribute the affected attribute, or {@code null} for whole-type changes
 * @param detail    extra context (e.g. {@code "oldType -> newType"}), or {@code null}
 * @param impact    the operational risk of applying this change
 */
public record SchemaChange(ChangeKind kind, String typeCode, String attribute, String detail, Impact impact) {

    /**
     * Renders this change as a stable, human-readable line, e.g.
     * {@code "[DESTRUCTIVE] REMOVE_ATTRIBUTE Product.oldField"}.
     *
     * @return a one-line description including impact, kind, target and detail
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(impact).append("] ").append(kind).append(' ').append(typeCode);
        if (attribute != null) {
            sb.append('.').append(attribute);
        }
        if (detail != null) {
            sb.append(" (").append(detail).append(')');
        }
        return sb.toString();
    }
}
