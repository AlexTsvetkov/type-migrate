package com.sapcommercetools.typemigrate;

import java.util.Objects;

/**
 * Connection configuration for a live SAP Commerce HAC (Hybris Administration
 * Console) endpoint, used by {@link HacClient} and {@link LiveTypeModelReader}.
 *
 * <p>Values are read from the environment via {@link #fromEnv()} so the same
 * binary can point at any environment without recompilation:
 * <ul>
 *   <li>{@code COMMERCE_BASE_URL} — e.g. {@code https://localhost:9002} (required
 *       for live reads; when unset callers should fall back to offline behaviour).</li>
 *   <li>{@code COMMERCE_USER} — HAC user (default {@code admin}).</li>
 *   <li>{@code COMMERCE_PASSWORD} — HAC password (default {@code nimda}).</li>
 *   <li>{@code COMMERCE_INSECURE_TLS} — {@code true} to trust self-signed certs
 *       (local dev only; default {@code false}).</li>
 * </ul>
 *
 * @param baseUrl     root URL of the running instance (no trailing slash)
 * @param user        HAC login user
 * @param password    HAC login password
 * @param insecureTls whether to trust all TLS certificates (local self-signed dev)
 */
public record HacConfig(String baseUrl, String user, String password, boolean insecureTls) {

    /** Env var holding the base URL of the running instance. */
    public static final String ENV_BASE_URL = "COMMERCE_BASE_URL";
    /** Env var holding the HAC user. */
    public static final String ENV_USER = "COMMERCE_USER";
    /** Env var holding the HAC password. */
    public static final String ENV_PASSWORD = "COMMERCE_PASSWORD";
    /** Env var toggling insecure (trust-all) TLS. */
    public static final String ENV_INSECURE_TLS = "COMMERCE_INSECURE_TLS";

    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASSWORD = "nimda";

    public HacConfig {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(password, "password must not be null");
        // Normalise: strip a single trailing slash so path concatenation is clean.
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    /**
     * Builds a configuration from environment variables.
     *
     * @return a config populated from the {@code COMMERCE_*} env vars
     * @throws IllegalStateException if {@code COMMERCE_BASE_URL} is unset/blank
     */
    public static HacConfig fromEnv() {
        String base = System.getenv(ENV_BASE_URL);
        if (base == null || base.isBlank()) {
            throw new IllegalStateException(
                    ENV_BASE_URL + " is not set — cannot build a live HAC configuration.");
        }
        String user = orDefault(System.getenv(ENV_USER), DEFAULT_USER);
        String password = orDefault(System.getenv(ENV_PASSWORD), DEFAULT_PASSWORD);
        boolean insecure = Boolean.parseBoolean(System.getenv(ENV_INSECURE_TLS));
        return new HacConfig(base.trim(), user, password, insecure);
    }

    /**
     * Reports whether a live configuration is available in the environment
     * (i.e. {@code COMMERCE_BASE_URL} is set). Callers use this to decide
     * whether to attempt a live read or fall back to offline behaviour.
     *
     * @return {@code true} if {@code COMMERCE_BASE_URL} is present and non-blank
     */
    public static boolean isConfigured() {
        String base = System.getenv(ENV_BASE_URL);
        return base != null && !base.isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
