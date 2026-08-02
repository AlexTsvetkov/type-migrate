package com.sapcommercetools.typemigrate;

/**
 * Diffs a target type model against the deployed schema and returns an ordered, reversible migration plan with an impact estimate.
 *
 * <p>This is the core abstraction of <b>type-migrate</b>. The starter implementation
 * below is intentionally minimal — a foundation that documents the intended
 * contract and gives tests something real to exercise.
 */
public final class MigrationPlanner {

    /**
     * Returns a human-readable description of what this component does.
     * Replace with the real behaviour as the project grows.
     */
    public String describe() {
        return "type-migrate: Flyway for the SAP Commerce type system — versioned, reviewable schema migrations with a dry-run diff and safe rollback.";
    }

    /**
     * Placeholder for the primary operation. Kept trivial and total so the
     * scaffold builds and tests pass on a clean checkout.
     *
     * @param input a caller-supplied token
     * @return {@code true} when the input is non-blank
     */
    public boolean accepts(String input) {
        return input != null && !input.isBlank();
    }
}
