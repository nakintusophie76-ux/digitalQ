package com.kiu.digitalq.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON reader/writer.
 *
 * <p>Only the pieces needed by the KIU digitalQ API are supported: objects, arrays,
 * strings, numbers, booleans and {@code null}. This keeps the whole application
 * buildable with nothing but a JDK.</p>
 */
public final class Json {

    private Json() {
    }

    public static JsonObject object() {
        return new JsonObject();
    }

    public static JsonArray array() {
        return new JsonArray();
    }

    public static JsonObject parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof JsonObject object)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return object;
    }

    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Nothing to parse");
        }
        return new Parser(text).parseDocument();
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(out, text);
        } else if (value instanceof Boolean flag) {
            out.append(flag.booleanValue());
        } else if (value instanceof Number number) {
            writeNumber(out, number);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                writeValue(out, java.lang.reflect.Array.get(value, i));
            }
            out.append(']');
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeNumber(StringBuilder out, Number number) {
        if (number instanceof Double d && (d.isNaN() || d.isInfinite())) {
            out.append("null");
            return;
        }
        if (number instanceof Float f && (f.isNaN() || f.isInfinite())) {
            out.append("null");
            return;
        }
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                out.append((long) d);
            } else {
                out.append(d);
            }
            return;
        }
        out.append(number.longValue());
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** Insertion-ordered JSON object with a few convenient typed getters. */
    public static final class JsonObject extends LinkedHashMap<String, Object> {

        public JsonObject() {
        }

        public JsonObject(Map<String, Object> source) {
            super(source);
        }

        @Override
        public JsonObject put(String key, Object value) {
            super.put(key, value);
            return this;
        }

        public JsonObject set(String key, Object value) {
            return put(key, value);
        }

        public String str(String key) {
            Object value = get(key);
            return value == null ? "" : String.valueOf(value);
        }

        public String str(String key, String fallback) {
            String value = str(key);
            return value.isEmpty() ? fallback : value;
        }

        public boolean bool(String key) {
            Object value = get(key);
            if (value instanceof Boolean flag) {
                return flag;
            }
            return value != null && Boolean.parseBoolean(String.valueOf(value));
        }

        public int integer(String key) {
            return (int) decimal(key);
        }

        public long lng(String key) {
            return (long) decimal(key);
        }

        public double dbl(String key) {
            Object value = get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value == null) {
                return 0;
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        private double decimal(String key) {
            Object value = get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value == null) {
                return 0;
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        public JsonObject obj(String key) {
            Object value = get(key);
            return value instanceof JsonObject object ? object : new JsonObject();
        }

        public JsonArray arr(String key) {
            Object value = get(key);
            return value instanceof JsonArray array ? array : new JsonArray();
        }

        public boolean ok() {
            return bool("ok");
        }

        public boolean isNull(String key) {
            return get(key) == null;
        }
    }

    /** Simple JSON array with covariant add(). */
    public static final class JsonArray extends ArrayList<Object> {

        public JsonArray() {
        }

        public JsonArray with(Object value) {
            super.add(value);
            return this;
        }

        public JsonObject obj(int index) {
            if (index < 0 || index >= size()) {
                return new JsonObject();
            }
            Object value = get(index);
            return value instanceof JsonObject object ? object : new JsonObject();
        }

        public String str(int index) {
            if (index < 0 || index >= size()) {
                return "";
            }
            Object value = get(index);
            return value == null ? "" : String.valueOf(value);
        }

        public List<String> strings() {
            List<String> result = new ArrayList<>(size());
            for (Object item : this) {
                result.add(item == null ? "" : String.valueOf(item));
            }
            return result;
        }
    }

    private static final class Parser {

        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        Object parseDocument() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (index < text.length()) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private Object parseValue() {
            if (index >= text.length()) {
                throw error("Unexpected end of input");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        private JsonObject parseObject() {
            expect('{');
            JsonObject object = new JsonObject();
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("Expected a property name");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                object.put(key, parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    index++;
                    continue;
                }
                if (c == '}') {
                    index++;
                    return object;
                }
                throw error("Expected ',' or '}'");
            }
        }

        private JsonArray parseArray() {
            expect('[');
            JsonArray array = new JsonArray();
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return array;
            }
            while (true) {
                skipWhitespace();
                array.add(parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    index++;
                    continue;
                }
                if (c == ']') {
                    index++;
                    return array;
                }
                throw error("Expected ',' or ']'");
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (index >= text.length()) {
                        throw error("Unfinished escape sequence");
                    }
                    char escape = text.charAt(index++);
                    switch (escape) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (index + 4 > text.length()) {
                                throw error("Invalid unicode escape");
                            }
                            String hex = text.substring(index, index + 4);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                throw error("Invalid unicode escape");
                            }
                            index += 4;
                        }
                        default -> throw error("Invalid escape '\\" + escape + "'");
                    }
                } else if (c < 0x20) {
                    throw error("Control character in string");
                } else {
                    out.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private Object parseNumber() {
            int start = index;
            if (peek() == '-') {
                index++;
            }
            boolean fraction = false;
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c >= '0' && c <= '9') {
                    index++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    fraction = fraction || c == '.' || c == 'e' || c == 'E';
                    index++;
                } else {
                    break;
                }
            }
            String token = text.substring(start, index);
            try {
                if (fraction) {
                    return Double.parseDouble(token);
                }
                return Long.parseLong(token);
            } catch (NumberFormatException ex) {
                try {
                    return Double.parseDouble(token);
                } catch (NumberFormatException second) {
                    throw error("Invalid number '" + token + "'");
                }
            }
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, index)) {
                throw error("Invalid literal");
            }
            index += word.length();
            return value;
        }

        private void expect(char expected) {
            if (index >= text.length() || text.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private char peek() {
            if (index >= text.length()) {
                throw error("Unexpected end of input");
            }
            return text.charAt(index);
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    break;
                }
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + index);
        }
    }
}
