package com.sapcommercetools.typemigrate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MigrationPlannerTest {

    private final MigrationPlanner subject = new MigrationPlanner();

    private TypeModel product(Map<String, String> attrs) {
        return TypeModel.builder().type("Product", attrs).build();
    }

    @Test
    void add_type_is_safe() {
        TypeModel current = TypeModel.empty();
        TypeModel target = product(Map.of("code", "String"));

        List<SchemaChange> changes = MigrationPlanner.diff(current, target);

        assertEquals(1, changes.size());
        assertEquals(ChangeKind.ADD_TYPE, changes.get(0).kind());
        assertEquals("Product", changes.get(0).typeCode());
        assertEquals(Impact.SAFE, changes.get(0).impact());
    }

    @Test
    void remove_type_is_destructive() {
        TypeModel current = product(Map.of("code", "String"));
        TypeModel target = TypeModel.empty();

        List<SchemaChange> changes = MigrationPlanner.diff(current, target);

        assertEquals(1, changes.size());
        assertEquals(ChangeKind.REMOVE_TYPE, changes.get(0).kind());
        assertEquals(Impact.DESTRUCTIVE, changes.get(0).impact());
        assertTrue(subject.plan(current, target).isDestructive());
    }

    @Test
    void add_attribute_is_safe() {
        TypeModel current = product(Map.of("code", "String"));
        TypeModel target = product(Map.of("code", "String", "price", "Double"));

        List<SchemaChange> changes = MigrationPlanner.diff(current, target);

        assertEquals(1, changes.size());
        assertEquals(ChangeKind.ADD_ATTRIBUTE, changes.get(0).kind());
        assertEquals("price", changes.get(0).attribute());
        assertEquals(Impact.SAFE, changes.get(0).impact());
    }

    @Test
    void remove_attribute_is_destructive() {
        TypeModel current = product(Map.of("code", "String", "legacy", "String"));
        TypeModel target = product(Map.of("code", "String"));

        List<SchemaChange> changes = MigrationPlanner.diff(current, target);

        assertEquals(1, changes.size());
        assertEquals(ChangeKind.REMOVE_ATTRIBUTE, changes.get(0).kind());
        assertEquals("legacy", changes.get(0).attribute());
        assertEquals(Impact.DESTRUCTIVE, changes.get(0).impact());
    }

    @Test
    void change_attribute_type_is_review_with_detail() {
        TypeModel current = product(Map.of("code", "String", "price", "Integer"));
        TypeModel target = product(Map.of("code", "String", "price", "Double"));

        List<SchemaChange> changes = MigrationPlanner.diff(current, target);

        assertEquals(1, changes.size());
        SchemaChange c = changes.get(0);
        assertEquals(ChangeKind.CHANGE_ATTRIBUTE_TYPE, c.kind());
        assertEquals(Impact.REVIEW, c.impact());
        assertEquals("Integer -> Double", c.detail());
    }

    @Test
    void ordering_is_deterministic_by_type_then_attribute() {
        // Zeta and Alpha both exist on each side, so we get attribute-level
        // changes we can order; Alpha (removed whole type) also present.
        TypeModel current = TypeModel.builder()
                .type("Zeta", Map.of("code", "String"))
                .type("Alpha", Map.of("code", "String"))
                .build();
        TypeModel target = TypeModel.builder()
                .type("Zeta", Map.of("code", "String", "bbb", "String", "aaa", "String"))
                .build();

        List<SchemaChange> changes = MigrationPlanner.diff(current, target);

        // Alpha sorts before Zeta by type code, regardless of change kind.
        assertEquals("Alpha", changes.get(0).typeCode());
        assertEquals(ChangeKind.REMOVE_TYPE, changes.get(0).kind());

        // Zeta's changes: added attributes ordered alphabetically (aaa before bbb).
        List<SchemaChange> zeta = changes.stream().filter(c -> c.typeCode().equals("Zeta")).toList();
        assertEquals(2, zeta.size());
        assertEquals("aaa", zeta.get(0).attribute());
        assertEquals("bbb", zeta.get(1).attribute());
    }

    @Test
    void summary_counts_by_impact() {
        TypeModel current = TypeModel.builder()
                .type("Product", Map.of("code", "String", "old", "String", "price", "Integer"))
                .build();
        TypeModel target = TypeModel.builder()
                .type("Product", Map.of("code", "String", "price", "Double", "brand", "String"))
                .build();

        MigrationPlan plan = subject.plan(current, target);
        Map<Impact, Integer> summary = plan.summary();

        // add brand (SAFE), remove old (DESTRUCTIVE), change price type (REVIEW).
        assertEquals(1, summary.get(Impact.SAFE));
        assertEquals(1, summary.get(Impact.REVIEW));
        assertEquals(1, summary.get(Impact.DESTRUCTIVE));
        assertEquals(3, plan.changes().size());
    }

    @Test
    void rollback_is_the_inverse_plan() {
        TypeModel current = product(Map.of("code", "String"));
        TypeModel target = product(Map.of("code", "String", "price", "Double"));

        MigrationPlan forward = subject.plan(current, target);
        MigrationPlan back = forward.rollback();

        // Forward adds an attribute; rollback must remove it.
        assertEquals(ChangeKind.ADD_ATTRIBUTE, forward.changes().get(0).kind());
        assertEquals(ChangeKind.REMOVE_ATTRIBUTE, back.changes().get(0).kind());
        assertEquals("price", back.changes().get(0).attribute());
        assertTrue(back.isDestructive());
        // Rollback of the rollback returns to the forward direction.
        assertEquals(ChangeKind.ADD_ATTRIBUTE, back.rollback().changes().get(0).kind());
    }

    @Test
    void dry_run_contains_destructive_warning_and_change_lines() {
        TypeModel current = product(Map.of("code", "String", "legacy", "String"));
        TypeModel target = product(Map.of("code", "String"));

        String report = subject.dryRun(current, target);

        assertTrue(report.contains("WARNING"), "destructive report must warn");
        assertTrue(report.contains("DESTRUCTIVE"));
        assertTrue(report.contains("REMOVE_ATTRIBUTE Product.legacy"));
    }

    @Test
    void dry_run_no_warning_when_safe() {
        TypeModel current = product(Map.of("code", "String"));
        TypeModel target = product(Map.of("code", "String", "price", "Double"));

        String report = subject.dryRun(current, target);

        assertFalse(report.contains("WARNING"));
        assertTrue(report.contains("ADD_ATTRIBUTE Product.price"));
    }

    @Test
    void identical_models_produce_empty_plan() {
        TypeModel model = product(Map.of("code", "String"));

        assertTrue(subject.plan(model, model).isEmpty());
        assertTrue(subject.dryRun(model, model).contains("no changes"));
    }

    @Test
    void describes_itself_and_accepts_input() {
        assertTrue(subject.describe().startsWith("type-migrate"));
        assertTrue(subject.accepts("cart-123"));
        assertFalse(subject.accepts(" "));
        assertFalse(subject.accepts(null));
    }
}
