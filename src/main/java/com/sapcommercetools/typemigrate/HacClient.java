package com.sapcommercetools.typemigrate;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A small, self-contained HTTP client for HAC (Hybris Administration Console)
 * that performs Spring-Security form login and executes FlexibleSearch queries.
 *
 * <p>This mirrors the auth + flexsearch approach used by the sibling
 * {@code flexsearch-dx} tool but is intentionally standalone (no cross-project
 * dependency). It uses only {@link java.net.http.HttpClient} plus the tiny
 * {@link HacJson} parser, honouring the project's zero-dependency, pure-JDK
 * constraint.
 *
 * <h2>Flow (per LIVE_CONTRACT.md)</h2>
 * <ol>
 *   <li>{@code GET /login} → read the {@code _csrf} token, keep the JSESSIONID cookie.</li>
 *   <li>{@code POST /j_spring_security_check} with {@code j_username}, {@code j_password},
 *       {@code _csrf} → 302 to {@code /} on success.</li>
 *   <li>{@code GET /} → read the AJAX CSRF token from the {@code <meta name="_csrf">} tag.</li>
 *   <li>{@code POST /console/flexsearch/execute} with the {@code X-CSRF-TOKEN} header.</li>
 * </ol>
 *
 * <p>A {@link CookieManager} on the underlying client carries the session cookie
 * automatically across all calls.
 */
public final class HacClient implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Matches the {@code <meta name="_csrf" content="...">} token on any authed page. */
    private static final Pattern META_CSRF =
            Pattern.compile("<meta\\s+name=\"_csrf\"\\s+content=\"([^\"]+)\"");
    /** Matches the hidden {@code _csrf} form input on the login page. */
    private static final Pattern INPUT_CSRF =
            Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private final HacConfig config;
    private final HttpClient http;
    private boolean loggedIn;
    private String ajaxCsrf;

    /**
     * Creates a client for the given configuration. The session is established
     * lazily on the first {@link #executeFlexibleSearch} call, or eagerly via
     * {@link #login()}.
     *
     * @param config connection configuration (must not be {@code null})
     */
    public HacClient(HacConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER) // we inspect the 302 ourselves
                .cookieHandler(new CookieManager())
                .connectTimeout(TIMEOUT);
        if (config.insecureTls()) {
            builder.sslContext(trustAllSslContext());
        }
        this.http = builder.build();
    }

    /**
     * Performs the login handshake (idempotent — subsequent calls are no-ops).
     *
     * @throws IOException          if a request fails
     * @throws InterruptedException if the calling thread is interrupted
     * @throws IllegalStateException if authentication does not succeed
     */
    public synchronized void login() throws IOException, InterruptedException {
        if (loggedIn) {
            return;
        }

        // 1. GET /login → CSRF token + session cookie.
        HttpResponse<String> loginPage = send(HttpRequest.newBuilder()
                .uri(uri("/login"))
                .GET()
                .build());
        String loginCsrf = firstMatch(META_CSRF, loginPage.body());
        if (loginCsrf == null) {
            loginCsrf = firstMatch(INPUT_CSRF, loginPage.body());
        }
        if (loginCsrf == null) {
            throw new IllegalStateException("Could not find _csrf token on /login page.");
        }

        // 2. POST credentials.
        String form = form(Map.of(
                "j_username", config.user(),
                "j_password", config.password(),
                "_csrf", loginCsrf));
        HttpResponse<String> auth = send(HttpRequest.newBuilder()
                .uri(uri("/j_spring_security_check"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());
        // Success is a 302 redirect to "/". A 200 back to the login form means bad credentials.
        int sc = auth.statusCode();
        if (sc != 302 && sc != 303 && sc != 200) {
            throw new IllegalStateException("Login POST returned unexpected status " + sc);
        }
        String location = auth.headers().firstValue("location").orElse("");
        if (sc == 200 || location.contains("login") || location.contains("error")) {
            throw new IllegalStateException(
                    "HAC login failed (status " + sc + ", location '" + location + "') — check credentials.");
        }

        // 3. GET / → AJAX CSRF token for subsequent POSTs.
        HttpResponse<String> home = send(HttpRequest.newBuilder()
                .uri(uri("/"))
                .GET()
                .build());
        this.ajaxCsrf = firstMatch(META_CSRF, home.body());
        if (this.ajaxCsrf == null) {
            throw new IllegalStateException("Could not read AJAX _csrf token from authed home page.");
        }
        this.loggedIn = true;
    }

    /**
     * Executes a FlexibleSearch query against {@code /console/flexsearch/execute}
     * and returns the parsed rows/headers. Logs in first if necessary.
     *
     * @param query    the FlexibleSearch query (e.g. {@code SELECT {code} FROM {ComposedType}})
     * @param maxCount maximum rows to return
     * @return the parsed result
     * @throws IOException          if the request fails
     * @throws InterruptedException if the calling thread is interrupted
     * @throws IllegalStateException if the server reports a query exception
     */
    public HacJson.FlexResult executeFlexibleSearch(String query, int maxCount)
            throws IOException, InterruptedException {
        Objects.requireNonNull(query, "query must not be null");
        login();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("flexibleSearchQuery", query);
        params.put("maxCount", Integer.toString(maxCount));
        params.put("user", config.user());
        params.put("locale", "en");
        params.put("dataSource", "master");
        params.put("commit", "false");

        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .uri(uri("/console/flexsearch/execute"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-CSRF-TOKEN", ajaxCsrf)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)))
                .build());

        if (resp.statusCode() != 200) {
            throw new IllegalStateException(
                    "flexsearch execute returned status " + resp.statusCode() + ": "
                            + trim(resp.body()));
        }

        HacJson.FlexResult result = HacJson.parseFlexResult(resp.body());
        if (result.hasError()) {
            throw new IllegalStateException("flexsearch query failed: " + result.exception()
                    + "  [query: " + query + "]");
        }
        return result;
    }

    @Override
    public void close() {
        // HttpClient has no explicit close in Java 21; nothing to release.
    }

    // ------------------------------------------------------------------ helpers

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create(config.baseUrl() + path);
    }

    private static String firstMatch(Pattern pattern, String body) {
        if (body == null) {
            return null;
        }
        Matcher m = pattern.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static String form(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String trim(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }

    /**
     * Builds an all-trusting {@link SSLContext} for local self-signed dev
     * instances ({@code COMMERCE_INSECURE_TLS=true}). Never use against
     * production hosts.
     */
    private static SSLContext trustAllSslContext() {
        try {
            TrustManager[] trustAll = {new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Could not build insecure SSL context", e);
        }
    }

    /** @return an immutable snapshot of the AJAX CSRF token (test/diagnostic aid). */
    List<String> debugState() {
        return List.of("loggedIn=" + loggedIn, "hasCsrf=" + (ajaxCsrf != null));
    }
}
