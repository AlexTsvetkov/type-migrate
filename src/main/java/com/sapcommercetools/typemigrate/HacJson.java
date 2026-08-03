package com.sapcommercetools.typemigrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A tiny, dependency-free JSON reader specialised for the FlexibleSearch
 * execution response returned by HAC's {@code /console/flexsearch/execute}.
 *
 * <p>It is <em>not</em> a general JSON library — it parses just enough of the
 * grammar (objects, arrays, strings with escapes, numbers, booleans, null) to
 * extract the three fields this tool cares about:
 * <ul>
 *   <li>{@code headers} — the column labels, as a {@code List<String>};</li>
 *   <li>{@code resultList} — the rows, as a {@code List<List<String>>}
 *       (every scalar cell is rendered to its string form; {@code null}
 *       cells become {@code null});</li>
 *   <li>{@code exception} — the server-side error text, or {@code null} on success.</li>
 * </ul>
 *
 * <p>The parser is deliberately small and self-contained so the project keeps
 * its "pure JDK, zero dependencies" constraint.
 */
final class HacJson {

    /**
     * The parsed, tool-relevant slice of a flexsearch response.
     *
     * @param headers    column labels (never {@code null}; may be empty)
     * @param rows       result rows, each a list of stringified cell values
     * @param exception  server error text, or {@code null} when the query succeeded
     * @param resultCount the server-reported row count (0 when absent)
     */
    record FlexResult(List<String> headers, List<List<String>> rows, String exception, int resultCount) {
        boolean hasError() {
            return exception != null && !exception.isBlank();
        }
    }

    private HacJson() {
    }

    /**
     * Parses a flexsearch response body into a {@link FlexResult}.
     *
     * @param json the raw JSON body (must not be {@code null})
     * @return the extracted headers, rows and exception
     * @throws IllegalArgumentException if the body is not a JSON object
     */
    @SuppressWarnings("unchecked")
    static FlexResult parseFlexResult(String json) {
        Object root = parse(json);
        if (!(root instanceof java.util.Map<?, ?> map)) {
            throw new IllegalArgumentException("flexsearch response was not a JSON object");
        }

        List<String> headers = new ArrayList<>();
        Object h = map.get("headers");
        if (h instanceof List<?> hl) {
            for (Object o : hl) {
                headers.add(o == null ? null : String.valueOf(o));
            }
        }

        List<List<String>> rows = new ArrayList<>();
        Object rl = map.get("resultList");
        if (rl instanceof List<?> outer) {
            for (Object rowObj : outer) {
                List<String> row = new ArrayList<>();
                if (rowObj instanceof List<?> inner) {
                    for (Object cell : inner) {
                        row.add(cell == null ? null : String.valueOf(cell));
                    }
                }
                rows.add(row);
            }
        }

        Object exc = map.get("exception");
        String exception = exc == null ? null : String.valueOf(exc);

        int count = 0;
        Object rc = map.get("resultCount");
        if (rc instanceof Number n) {
            count = n.intValue();
        }

        return new FlexResult(headers, rows, exception, count);
    }

    // ---------------------------------------------------------------------
    // Minimal recursive-descent JSON parser.
    // Values: Map<String,Object>, List<Object>, String, Double, Long, Boolean, null.
    // ---------------------------------------------------------------------

    /**
     * Parses an arbitrary JSON document.
     *
     * @param json the JSON text (must not be {@code null})
     * @return the parsed value tree ({@link java.util.Map}, {@link List},
     *         {@link String}, {@link Long}, {@link Double}, {@link Boolean} or {@code null})
     */
    static Object parse(String json) {
        Objects.requireNonNull(json, "json must not be null");
        Parser p = new Parser(json);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw new IllegalArgumentException("trailing content after JSON value at index " + p.pos);
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue() {
            skipWs();
            if (atEnd()) {
                throw err("unexpected end of input");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        private java.util.Map<String, Object> readObject() {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWs();
                char c = next();
                if (c == ',') {
                    continue;
                }
                if (c == '}') {
                    break;
                }
                throw err("expected ',' or '}' in object");
            }
            return map;
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = readValue();
                list.add(value);
                skipWs();
                char c = next();
                if (c == ',') {
                    continue;
                }
                if (c == ']') {
                    break;
                }
                throw err("expected ',' or ']' in array");
            }
            return list;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw err("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw err("invalid escape '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Boolean readBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw err("invalid literal");
        }

        private Object readNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw err("invalid literal");
        }

        private Object readNumber() {
            int start = pos;
            boolean floating = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    floating = true;
                    pos++;
                } else {
                    break;
                }
            }
            String num = s.substring(start, pos);
            if (num.isEmpty()) {
                throw err("invalid number");
            }
            if (floating) {
                return Double.parseDouble(num);
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return Double.parseDouble(num);
            }
        }

        private char peek() {
            skipWs();
            if (atEnd()) {
                throw err("unexpected end of input");
            }
            return s.charAt(pos);
        }

        private char next() {
            if (atEnd()) {
                throw err("unexpected end of input");
            }
            return s.charAt(pos++);
        }

        private void expect(char c) {
            char actual = next();
            if (actual != c) {
                throw err("expected '" + c + "' but found '" + actual + "'");
            }
        }

        private IllegalArgumentException err(String message) {
            return new IllegalArgumentException("JSON parse error at index " + pos + ": " + message);
        }
    }
}
