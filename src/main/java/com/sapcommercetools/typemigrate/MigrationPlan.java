package com.sapcommercetools.typemigrate;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A computed, ordered set of {@link SchemaChange}s that migrates a
 * <em>current</em> {@link TypeModel} to a <em>target</em> one, together with
 * the two endpoints needed to reverse it.
 *
 * <p>The plan is reversible: {@link #rollback()} returns the inverse plan (the
 * diff from target back to current), which is the core value proposition of
 * type-migrate — every forward migration comes with a computed undo.
 */
public final class MigrationPlan {

    private final TypeModel current;
    private final TypeModel target;
    private final List<SchemaChange> changes;

    /**
     * @param current the source model
     * @param target  the destination model
     * @param changes the ordered changes from current to target
     */
    MigrationPlan(TypeModel current, TypeModel target, List<SchemaChange> changes) {
        this.current = Objects.requireNonNull(current);
        this.target = Objects.requireNonNull(target);
        this.changes = Collections.unmodifiableList(changes);
    }

    /** @return the ordered, unmodifiable list of changes. */
    public List<SchemaChange> changes() {
        return changes;
    }

    /** @return {@code true} if any change has {@link Impact#DESTRUCTIVE} impact. */
    public boolean isDestructive() {
        return changes.stream().anyMatch(c -> c.impact() == Impact.DESTRUCTIVE);
    }

    /** @return {@code true} if the plan contains no changes. */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * Counts changes by {@link Impact}. All impact levels are present as keys,
     * defaulting to {@code 0}.
     *
     * @return an {@link Impact}-keyed map of counts (iteration follows enum order)
     */
    public Map<Impact, Integer> summary() {
        Map<Impact, Integer> counts = new EnumMap<>(Impact.class);
        for (Impact i : Impact.values()) {
            counts.put(i, 0);
        }
        for (SchemaChange c : changes) {
            counts.merge(c.impact(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Builds the inverse plan that undoes this one, by diffing in the opposite
     * direction ({@code target → current}). Applying this plan on top of the
     * target model restores the current model.
     *
     * @return the reverse {@link MigrationPlan}
     */
    public MigrationPlan rollback() {
        return new MigrationPlan(target, current, MigrationPlanner.diff(target, current));
    }

    @Override
    public String toString() {
        return "MigrationPlan" + changes.stream().map(SchemaChange::describe).toList();
    }
}
