package com.sapcommercetools.typemigrate.examples;

import com.sapcommercetools.typemigrate.Impact;
import com.sapcommercetools.typemigrate.MigrationPlan;
import com.sapcommercetools.typemigrate.MigrationPlanner;
import com.sapcommercetools.typemigrate.SchemaChange;
import com.sapcommercetools.typemigrate.TypeModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runnable, self-contained tutorial for <b>type-migrate</b>.
 *
 * <p>It builds a <em>current</em> {@link TypeModel} and a <em>target</em> one,
 * then uses {@link MigrationPlanner} to:
 * <ul>
 *   <li>{@code diff(current, target)} — the raw list of {@link SchemaChange}s,
 *       each carrying an {@link Impact} (SAFE / REVIEW / DESTRUCTIVE);</li>
 *   <li>{@code plan(current, target)} — a {@link MigrationPlan} exposing
 *       {@code isDestructive()} and {@code summary()};</li>
 *   <li>{@code dryRun(current, target)} — a human-readable report;</li>
 *   <li>{@code plan.rollback()} — the inverse plan, proving forward+rollback
 *       round-trips back to the current model.</li>
 * </ul>
 *
 * <p>This is the fully offline path: it never touches HAC or a live instance
 * (no {@code LiveTypeModelReader} here). Pure JDK only.
 */
public final class Example {

    private Example() {
        // tutorial entry-point only
    }

    public static void main(String[] args) {
        System.out.println("=== type-migrate: diff / plan / dryRun / rollback tutorial ===\n");

        // ---------------------------------------------------------------------
        // CURRENT model — what is deployed today.
        //   Product:  code:String, price:java.lang.Double, oldFlag:Boolean
        //   Customer: uid:String
        //   Legacy:   note:String            (a whole type that will be dropped)
        // ---------------------------------------------------------------------
        TypeModel current = TypeModel.builder()
                .type("Product", attrs(
                        "code", "java.lang.String",
                        "price", "java.lang.Double",
                        "oldFlag", "java.lang.Boolean"))
                .type("Customer", attrs(
                        "uid", "java.lang.String"))
                .type("Legacy", attrs(
                        "note", "java.lang.String"))
                .build();

        // ---------------------------------------------------------------------
        // TARGET model — the desired end state.
        //   Product:  code:String, price:java.math.BigDecimal (TYPE CHANGED),
        //             newFlag:Boolean (ADDED), oldFlag removed (REMOVED)
        //   Customer: uid:String (unchanged), email:String (ADDED)
        //   Legacy:   <gone>                 (whole type REMOVED - destructive)
        //   Voucher:  code:String            (whole new type ADDED)
        // ---------------------------------------------------------------------
        TypeModel target = TypeModel.builder()
                .type("Product", attrs(
                        "code", "java.lang.String",
                        "price", "java.math.BigDecimal",   // changed from Double -> REVIEW
                        "newFlag", "java.lang.Boolean"))   // added -> SAFE ; oldFlag dropped -> DESTRUCTIVE
                .type("Customer", attrs(
                        "uid", "java.lang.String",
                        "email", "java.lang.String"))      // added -> SAFE
                .type("Voucher", attrs(
                        "code", "java.lang.String"))       // whole new type -> SAFE
                .build();

        System.out.println("current model types: " + current);
        System.out.println("target  model types: " + target);
        System.out.println();

        // ---------------------------------------------------------------------
        // 1) diff(): the atomic changes, deterministically ordered by
        //    typeCode then attribute (whole-type changes sort first).
        // ---------------------------------------------------------------------
        System.out.println("-- diff(current, target): individual changes with impact --");
        List<SchemaChange> changes = MigrationPlanner.diff(current, target);
        for (SchemaChange c : changes) {
            System.out.println("  " + c.describe() + "   [isDestructive-impact=" + (c.impact() == Impact.DESTRUCTIVE) + "]");
        }
        System.out.println();

        // ---------------------------------------------------------------------
        // 2) plan(): wrap the diff with reversibility metadata + summary.
        // ---------------------------------------------------------------------
        MigrationPlanner planner = new MigrationPlanner();
        MigrationPlan plan = planner.plan(current, target);
        System.out.println("-- plan(current, target): rollup --");
        System.out.println("  change count   : " + plan.changes().size());
        System.out.println("  isDestructive(): " + plan.isDestructive());
        System.out.println("  isEmpty()      : " + plan.isEmpty());
        System.out.println("  summary()      : " + plan.summary()
                + "  (SAFE/REVIEW/DESTRUCTIVE counts)");
        System.out.println();

        // ---------------------------------------------------------------------
        // 3) dryRun(): the report you would drop into a PR comment or log.
        // ---------------------------------------------------------------------
        System.out.println("-- dryRun(current, target): human-readable report --");
        System.out.print(planner.dryRun(current, target));
        System.out.println();

        // ---------------------------------------------------------------------
        // 4) rollback(): the INVERSE plan (target -> current). Applying the
        //    forward changes then the rollback changes returns you to 'current'.
        //    We prove the inverse relationship: the rollback's changes equal
        //    diff(target, current), and re-applying rollback's diff to 'target'
        //    reconstructs exactly 'current'.
        // ---------------------------------------------------------------------
        System.out.println("-- rollback(): inverse plan (target -> current) --");
        MigrationPlan rollback = plan.rollback();
        for (SchemaChange c : rollback.changes()) {
            System.out.println("  " + c.describe());
        }
        System.out.println("  rollback summary(): " + rollback.summary());

        // Proof 1 — rollback is the opposite-direction diff: the rollback's own
        // change list must equal diff(target, current).
        List<SchemaChange> reverseDiff = MigrationPlanner.diff(target, current);
        boolean inverseMatches = rollback.changes().equals(reverseDiff);
        System.out.println("  rollback.changes() equals diff(target,current)? " + inverseMatches);

        // Proof 2 — rolling back twice returns the forward plan: rollback of the
        // rollback (current -> target again) must equal the original forward diff.
        boolean doubleRollbackIsForward = plan.rollback().rollback().changes().equals(plan.changes());
        System.out.println("  rollback().rollback() equals forward plan?      " + doubleRollbackIsForward);

        // Proof 3 — sanity: a model diffed against itself yields no changes.
        boolean noSelfChanges = MigrationPlanner.diff(current, current).isEmpty();
        System.out.println("  diff(current, current) is empty?                " + noSelfChanges);
        System.out.println();

        System.out.println("=== end of tutorial ===");
    }

    /** Builds an ordered attribute map (name -> declared type) readably. */
    private static Map<String, String> attrs(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("expected name/type pairs");
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }
}
