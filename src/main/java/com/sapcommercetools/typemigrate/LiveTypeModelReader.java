package com.sapcommercetools.typemigrate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the <em>deployed</em> type model straight from a running SAP Commerce
 * instance via HAC FlexibleSearch, so a {@link MigrationPlan} can be computed
 * against reality rather than a hand-maintained snapshot.
 *
 * <p>This is the "current" side of the diff: {@link #readDeployedModel(Set)}
 * returns a {@link TypeModel} built from live {@code ComposedType} /
 * {@code AttributeDescriptor} data, which callers then feed to
 * {@link MigrationPlanner#diff(TypeModel, TypeModel)} against a desired target.
 *
 * <h2>Queries (verified against the live sample instance)</h2>
 * <ul>
 *   <li>Composed types: {@code SELECT {code} FROM {ComposedType}} (optionally
 *       filtered to a scope via {@code WHERE {code} IN (...)}).</li>
 *   <li>Per-type attributes + declared type, via a three-way join:
 *       <pre>SELECT {ad:qualifier},{at:code}
 *FROM {AttributeDescriptor AS ad
 *      JOIN ComposedType AS ct ON {ad:enclosingType}={ct:pk}
 *      JOIN Type AS at ON {ad:attributeType}={at:pk}}
 *WHERE {ct:code}='&lt;code&gt;'</pre>
 *       Empirically this returns e.g. {@code isocode -> java.lang.String} for
 *       {@code Currency}.</li>
 * </ul>
 */
public final class LiveTypeModelReader {

    /** Generous cap; a single type rarely declares more than a few dozen attributes. */
    private static final int ATTR_MAX = 2000;
    /** Cap for the type listing itself. */
    private static final int TYPE_MAX = 20000;

    private final HacClient client;

    /**
     * @param client an authenticated-capable HAC client (must not be {@code null})
     */
    public LiveTypeModelReader(HacClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Convenience factory that builds a reader from environment configuration.
     *
     * @return a reader backed by a {@link HacClient} configured from {@code COMMERCE_*} env vars
     * @throws IllegalStateException if {@code COMMERCE_BASE_URL} is not set
     */
    public static LiveTypeModelReader fromEnv() {
        return new LiveTypeModelReader(new HacClient(HacConfig.fromEnv()));
    }

    /**
     * Reads the deployed type model.
     *
     * @param typeCodesOrNullForAll the set of composed-type codes to read, or
     *                              {@code null}/empty to read <em>all</em> composed types
     * @return a {@link TypeModel} of the requested types, each with its declared
     *         attribute-name → attribute-type map
     * @throws UncheckedIOException  if a live request fails
     * @throws IllegalStateException if a query is rejected by the server
     */
    public TypeModel readDeployedModel(Set<String> typeCodesOrNullForAll) {
        try {
            List<String> codes = listComposedTypes(typeCodesOrNullForAll);
            TypeModel.Builder builder = TypeModel.builder();
            for (String code : codes) {
                Map<String, String> attrs = readAttributes(code);
                builder.type(code, attrs);
            }
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read deployed type model from live instance", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading deployed type model", e);
        }
    }

    /**
     * Lists composed-type codes, optionally filtered to a scope.
     */
    private List<String> listComposedTypes(Set<String> scope) throws IOException, InterruptedException {
        String query = "SELECT {code} FROM {ComposedType}";
        if (scope != null && !scope.isEmpty()) {
            query += " WHERE {code} IN (" + inClause(scope) + ")";
        }
        HacJson.FlexResult result = client.executeFlexibleSearch(query, TYPE_MAX);
        List<String> codes = new java.util.ArrayList<>();
        for (List<String> row : result.rows()) {
            if (!row.isEmpty() && row.get(0) != null) {
                codes.add(row.get(0));
            }
        }
        // Deterministic order.
        codes.sort(String::compareTo);
        return codes;
    }

    /**
     * Reads declared attributes for a single composed type via the verified
     * three-way join, returning qualifier → attribute-type-code.
     */
    private Map<String, String> readAttributes(String code) throws IOException, InterruptedException {
        String query = "SELECT {ad:qualifier},{at:code} FROM {AttributeDescriptor AS ad"
                + " JOIN ComposedType AS ct ON {ad:enclosingType}={ct:pk}"
                + " JOIN Type AS at ON {ad:attributeType}={at:pk}}"
                + " WHERE {ct:code}='" + escape(code) + "'";
        HacJson.FlexResult result = client.executeFlexibleSearch(query, ATTR_MAX);
        Map<String, String> attrs = new LinkedHashMap<>();
        for (List<String> row : result.rows()) {
            if (row.size() >= 2 && row.get(0) != null) {
                String qualifier = row.get(0);
                String type = row.get(1); // may be null for exotic rows; keep as-is
                attrs.put(qualifier, type);
            }
        }
        return attrs;
    }

    /** Builds a {@code 'a','b','c'} list for an IN clause, escaping quotes. */
    private static String inClause(Set<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append('\'').append(escape(v)).append('\'');
        }
        return sb.toString();
    }

    /** Escapes single quotes for safe inlining into a FlexibleSearch literal. */
    private static String escape(String value) {
        return value.replace("'", "''");
    }
}
