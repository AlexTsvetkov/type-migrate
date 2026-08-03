package com.sapcommercetools.typemigrate;

import java.util.Objects;
import java.util.Set;

/**
 * Convenience facade that ties the {@link LiveTypeModelReader} to the
 * {@link MigrationPlanner}: read the current model from a running instance,
 * then diff a desired {@code target} against it.
 *
 * <p>This is the one-call path for the primary use case — "plan my migration
 * against what is actually deployed right now".
 */
public final class LiveMigrationPlanner {

    private final LiveTypeModelReader reader;

    /**
     * @param reader a live model reader (must not be {@code null})
     */
    public LiveMigrationPlanner(LiveTypeModelReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
    }

    /**
     * Builds a planner backed by environment configuration.
     *
     * @return a planner reading from the {@code COMMERCE_*}-configured instance
     * @throws IllegalStateException if {@code COMMERCE_BASE_URL} is not set
     */
    public static LiveMigrationPlanner fromEnv() {
        return new LiveMigrationPlanner(LiveTypeModelReader.fromEnv());
    }

    /**
     * Reads the live current model for {@code scopeTypes} and diffs {@code target}
     * against it.
     *
     * @param target     the desired type model
     * @param scopeTypes the composed types to read live (or {@code null}/empty for all)
     * @return the migration plan from the live model to {@code target}
     */
    public MigrationPlan planAgainstLive(TypeModel target, Set<String> scopeTypes) {
        Objects.requireNonNull(target, "target must not be null");
        TypeModel live = reader.readDeployedModel(scopeTypes);
        return new MigrationPlanner().plan(live, target);
    }

    /**
     * Static one-shot helper: read the live current model for {@code scopeTypes}
     * from the environment-configured instance and diff {@code target} against it.
     *
     * @param target     the desired type model
     * @param scopeTypes the composed types to read live (or {@code null}/empty for all)
     * @return the migration plan from the live model to {@code target}
     * @throws IllegalStateException if {@code COMMERCE_BASE_URL} is not set
     */
    public static MigrationPlan planAgainstLiveEnv(TypeModel target, Set<String> scopeTypes) {
        return fromEnv().planAgainstLive(target, scopeTypes);
    }
}
