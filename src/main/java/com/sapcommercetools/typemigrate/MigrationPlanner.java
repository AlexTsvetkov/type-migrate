package com.sapcommercetools.typemigrate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Diffs a target {@link TypeModel} against the current one and produces an
 * ordered, reversible {@link MigrationPlan} with a per-change {@link Impact}.
 *
 * <h2>Diff rules</h2>
 * <ul>
 *   <li>Type in target but not current → {@link ChangeKind#ADD_TYPE} ({@link Impact#SAFE}).</li>
 *   <li>Type in current but not target → {@link ChangeKind#REMOVE_TYPE} ({@link Impact#DESTRUCTIVE}).</li>
 *   <li>Attribute added to an existing type → {@link ChangeKind#ADD_ATTRIBUTE} ({@link Impact#SAFE}).</li>
 *   <li>Attribute removed from an existing type → {@link ChangeKind#REMOVE_ATTRIBUTE} ({@link Impact#DESTRUCTIVE}).</li>
 *   <li>Attribute type changed → {@link ChangeKind#CHANGE_ATTRIBUTE_TYPE} ({@link Impact#REVIEW}),
 *       with {@code detail = "oldType -> newType"}.</li>
 * </ul>
 *
 * <h2>Ordering</h2>
 * The change list is deterministic: sorted by {@code typeCode}, then by
 * {@code attribute} (whole-type changes, which have a {@code null} attribute,
 * sort before attribute-level changes for the same type).
 */
public final class MigrationPlanner {

    /**
     * Computes the ordered list of changes that migrates {@code current} into
     * {@code target}.
     *
     * @param current the deployed/source model (must not be {@code null})
     * @param target  the desired/destination model (must not be {@code null})
     * @return a deterministically ordered, unmodifiable-safe list of changes
     * @throws NullPointerException if either model is null
     */
    public static List<SchemaChange> diff(TypeModel current, TypeModel target) {
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(target, "target must not be null");

        Map<String, ItemType> cur = current.types();
        Map<String, ItemType> tgt = target.types();

        List<SchemaChange> changes = new ArrayList<>();

        // Union of all type codes so we can detect adds, removes and modifications.
        TreeSet<String> allCodes = new TreeSet<>();
        allCodes.addAll(cur.keySet());
        allCodes.addAll(tgt.keySet());

        for (String code : allCodes) {
            ItemType before = cur.get(code);
            ItemType after = tgt.get(code);

            if (before == null) {
                // Whole type is new.
                changes.add(new SchemaChange(ChangeKind.ADD_TYPE, code, null, null, Impact.SAFE));
                continue;
            }
            if (after == null) {
                // Whole type dropped.
                changes.add(new SchemaChange(ChangeKind.REMOVE_TYPE, code, null, null, Impact.DESTRUCTIVE));
                continue;
            }

            // Type exists on both sides — diff its attributes.
            Map<String, String> beforeAttrs = before.attributes();
            Map<String, String> afterAttrs = after.attributes();

            TreeSet<String> allAttrs = new TreeSet<>();
            allAttrs.addAll(beforeAttrs.keySet());
            allAttrs.addAll(afterAttrs.keySet());

            for (String attr : allAttrs) {
                String oldType = beforeAttrs.get(attr);
                String newType = afterAttrs.get(attr);

                if (oldType == null) {
                    changes.add(new SchemaChange(ChangeKind.ADD_ATTRIBUTE, code, attr, newType, Impact.SAFE));
                } else if (newType == null) {
                    changes.add(new SchemaChange(ChangeKind.REMOVE_ATTRIBUTE, code, attr, oldType, Impact.DESTRUCTIVE));
                } else if (!oldType.equals(newType)) {
                    changes.add(new SchemaChange(ChangeKind.CHANGE_ATTRIBUTE_TYPE, code, attr,
                            oldType + " -> " + newType, Impact.REVIEW));
                }
                // else: identical attribute — no change.
            }
        }

        // Deterministic ordering: by type code, then attribute (nulls first).
        changes.sort(Comparator
                .comparing(SchemaChange::typeCode)
                .thenComparing(SchemaChange::attribute, Comparator.nullsFirst(Comparator.naturalOrder())));

        return changes;
    }

    /**
     * Builds a full {@link MigrationPlan} (changes + reversibility metadata)
     * from {@code current} to {@code target}.
     *
     * @param current the deployed/source model
     * @param target  the desired/destination model
     * @return the migration plan
     */
    public MigrationPlan plan(TypeModel current, TypeModel target) {
        return new MigrationPlan(current, target, diff(current, target));
    }

    /**
     * Produces a human-readable dry-run report of the migration. Each change is
     * listed on its own line; when the plan is destructive the report starts
     * with a warning header.
     *
     * @param current the deployed/source model
     * @param target  the desired/destination model
     * @return a multi-line report suitable for logging or a PR comment
     */
    public String dryRun(TypeModel current, TypeModel target) {
        MigrationPlan plan = plan(current, target);
        StringBuilder sb = new StringBuilder();

        if (plan.isDestructive()) {
            sb.append("!! WARNING: DESTRUCTIVE migration — data loss possible. Review before applying.\n");
        }

        sb.append("Migration plan: ").append(plan.changes().size()).append(" change(s)\n");

        Map<Impact, Integer> summary = plan.summary();
        sb.append("Summary: ")
                .append(summary.get(Impact.SAFE)).append(" safe, ")
                .append(summary.get(Impact.REVIEW)).append(" review, ")
                .append(summary.get(Impact.DESTRUCTIVE)).append(" destructive\n");

        if (plan.isEmpty()) {
            sb.append("(no changes — models are identical)\n");
        } else {
            for (SchemaChange change : plan.changes()) {
                sb.append("  - ").append(change.describe()).append('\n');
            }
        }

        return sb.toString();
    }

    /**
     * Returns a human-readable description of what this component does.
     *
     * @return a one-line summary
     */
    public String describe() {
        return "type-migrate: Flyway for the SAP Commerce type system — versioned, reviewable schema "
                + "migrations with a dry-run diff and safe rollback.";
    }

    /**
     * Reports whether a caller-supplied token is usable (non-blank).
     *
     * @param input a caller-supplied token
     * @return {@code true} when the input is non-blank
     */
    public boolean accepts(String input) {
        return input != null && !input.isBlank();
    }
}
