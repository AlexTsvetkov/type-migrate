package com.sapcommercetools.typemigrate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Gated integration test for {@link LiveTypeModelReader}. Runs only when
 * {@code COMMERCE_BASE_URL} is set; otherwise skipped so CI stays green with
 * no live instance.
 *
 * <p>Run against the local sample with:
 * <pre>
 * COMMERCE_BASE_URL=https://localhost:9002 COMMERCE_USER=admin \
 *   COMMERCE_PASSWORD=nimda COMMERCE_INSECURE_TLS=true \
 *   gradle test --tests '*LiveTypeModelReaderIT'
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "COMMERCE_BASE_URL", matches = ".+")
class LiveTypeModelReaderIT {

    private static final Set<String> SCOPE = Set.of("Currency", "Language");

    @Test
    void reads_currency_and_language_with_attributes() {
        LiveTypeModelReader reader = LiveTypeModelReader.fromEnv();
        TypeModel model = reader.readDeployedModel(SCOPE);

        // Both requested types are present.
        assertTrue(model.types().containsKey("Currency"), "Currency should be present");
        assertTrue(model.types().containsKey("Language"), "Language should be present");

        // Non-empty attribute sets.
        ItemType currency = model.types().get("Currency");
        assertFalse(currency.attributes().isEmpty(), "Currency must have attributes");
        assertTrue(currency.attributes().containsKey("isocode"),
                "Currency must declare an 'isocode' attribute");
        assertEquals("java.lang.String", currency.attributes().get("isocode"));

        assertFalse(model.types().get("Language").attributes().isEmpty(),
                "Language must have attributes");
    }

    @Test
    void diff_against_target_missing_an_attribute_is_destructive_removal() {
        LiveTypeModelReader reader = LiveTypeModelReader.fromEnv();
        TypeModel live = reader.readDeployedModel(SCOPE);

        // Build a target identical to live, but with Currency.isocode removed.
        TypeModel.Builder tb = TypeModel.builder();
        live.types().forEach((code, it) -> {
            if (code.equals("Currency")) {
                Map<String, String> reduced = new LinkedHashMap<>(it.attributes());
                reduced.remove("isocode");
                tb.type(code, reduced);
            } else {
                tb.type(it);
            }
        });
        TypeModel target = tb.build();

        MigrationPlan plan = new MigrationPlanner().plan(live, target);

        assertTrue(plan.isDestructive(), "removing an attribute must be destructive");
        boolean hasIsocodeRemoval = plan.changes().stream().anyMatch(c ->
                c.kind() == ChangeKind.REMOVE_ATTRIBUTE
                        && c.typeCode().equals("Currency")
                        && "isocode".equals(c.attribute())
                        && c.impact() == Impact.DESTRUCTIVE);
        assertTrue(hasIsocodeRemoval,
                "plan should contain a DESTRUCTIVE REMOVE_ATTRIBUTE for Currency.isocode");
    }

    @Test
    void plan_against_live_convenience_reads_and_diffs() {
        LiveMigrationPlanner planner = LiveMigrationPlanner.fromEnv();

        // Target adds a brand-new attribute to Currency → a SAFE ADD_ATTRIBUTE.
        LiveTypeModelReader reader = LiveTypeModelReader.fromEnv();
        TypeModel live = reader.readDeployedModel(Set.of("Currency"));
        Map<String, String> attrs = new LinkedHashMap<>(live.types().get("Currency").attributes());
        attrs.put("tm_synthetic_marker", "java.lang.String");
        TypeModel target = TypeModel.builder().type("Currency", attrs).build();

        MigrationPlan plan = planner.planAgainstLive(target, Set.of("Currency"));

        assertFalse(plan.isEmpty(), "expected at least the added attribute");
        assertTrue(plan.changes().stream().anyMatch(c ->
                        c.kind() == ChangeKind.ADD_ATTRIBUTE
                                && "tm_synthetic_marker".equals(c.attribute())),
                "plan should contain the synthetic ADD_ATTRIBUTE");
    }
}
