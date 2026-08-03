package com.sapcommercetools.typemigrate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CI-safe unit tests for the tiny {@link HacJson} parser, fed the canned
 * flexsearch response shape documented in LIVE_CONTRACT.md §2. No live
 * instance required.
 */
class HacJsonParseTest {

    /** The exact response shape from LIVE_CONTRACT.md §2. */
    private static final String CANNED = """
            { "query":"<translated SQL>", "executionTime":22, "resultCount":5, "exception":null,
              "resultList":[["8796093087777","EUR"],["8796093120545","JPY"],["8796093153313","GBP"],
                            ["8796093186081","USD"],["8796093218849","CAD"]],
              "headers":["PK","p_isocode"],
              "rawExecution":false, "dataSourceId":"master" }
            """;

    @Test
    void parses_headers_rows_and_no_exception() {
        HacJson.FlexResult r = HacJson.parseFlexResult(CANNED);

        assertEquals(List.of("PK", "p_isocode"), r.headers());
        assertEquals(5, r.rows().size());
        assertEquals(5, r.resultCount());
        assertNull(r.exception());
        assertFalse(r.hasError());

        // First row is [pk, isocode].
        assertEquals("8796093087777", r.rows().get(0).get(0));
        assertEquals("EUR", r.rows().get(0).get(1));
        // Last row.
        assertEquals("CAD", r.rows().get(4).get(1));
    }

    @Test
    void surfaces_server_exception_as_error() {
        String errBody = """
                { "query":"bad", "resultCount":0, "exception":"cannot resolve type Foo",
                  "resultList":[], "headers":[] }
                """;
        HacJson.FlexResult r = HacJson.parseFlexResult(errBody);

        assertTrue(r.hasError());
        assertEquals("cannot resolve type Foo", r.exception());
        assertTrue(r.rows().isEmpty());
        assertTrue(r.headers().isEmpty());
    }

    @Test
    void parses_two_column_attribute_row_shape() {
        // Shape produced by the AttributeDescriptor 3-way join used by the reader.
        String body = """
                { "resultCount":2, "exception":null,
                  "resultList":[["isocode","java.lang.String"],["active","java.lang.Boolean"]],
                  "headers":["QualifierInternal","InternalCode"] }
                """;
        HacJson.FlexResult r = HacJson.parseFlexResult(body);

        assertEquals(2, r.rows().size());
        assertEquals("isocode", r.rows().get(0).get(0));
        assertEquals("java.lang.String", r.rows().get(0).get(1));
        assertEquals("java.lang.Boolean", r.rows().get(1).get(1));
    }

    @Test
    void handles_null_cells_and_escaped_strings() {
        String body = """
                { "resultCount":1, "exception":null,
                  "resultList":[["a\\"b",null]],
                  "headers":["h1","h2"] }
                """;
        HacJson.FlexResult r = HacJson.parseFlexResult(body);

        assertEquals(1, r.rows().size());
        assertEquals("a\"b", r.rows().get(0).get(0));
        assertNull(r.rows().get(0).get(1));
    }

    @Test
    void rejects_non_object_root() {
        assertThrows(IllegalArgumentException.class, () -> HacJson.parseFlexResult("[1,2,3]"));
    }
}
