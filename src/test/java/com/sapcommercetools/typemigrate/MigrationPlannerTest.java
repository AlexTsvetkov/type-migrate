package com.sapcommercetools.typemigrate;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MigrationPlannerTest {

    private final MigrationPlanner subject = new MigrationPlanner();

    @Test
    void describes_itself() {
        assertTrue(subject.describe().startsWith("type-migrate"));
    }

    @Test
    void accepts_non_blank_input() {
        assertTrue(subject.accepts("cart-123"));
        assertFalse(subject.accepts(" "));
        assertFalse(subject.accepts(null));
    }
}
