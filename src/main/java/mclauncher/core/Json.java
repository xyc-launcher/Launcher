package com.mclauncher.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 零依赖 JSON：递归下降解析 + 取值/序列化辅助。 */
public final class Json {
    private Json() {}

    /* ---------- 解析 ---------- */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.pos < p.text.length()) throw new RuntimeException("JSON 末尾有多余字符 @" + p.pos);
        return v;
    }

    private static final class Parser {
        final String text; int pos;
        Parser(String t) { this.text = t; }
        void ws() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }

        Object value() {
            ws();
            if (pos >= text.length()) throw new RuntimeException("意外的 JSON 结尾");
            char c = text.charAt(pos);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't' || c == 'f') return bool();
            if (c == 'n') return nul();
            return number();
        }

        Map<String,Object> object() {
            Map<String,Object> m = new LinkedHashMap<>();
            expect('{'); ws();
            if (peek() == '}') { pos++; return m; }
            while (true) {
                ws();
                String k = string();
                ws(); expect(':');
                Object v = value();
                m.put(k, v);
                ws();
                char c = next();
                if (c == ',') continue;
                if (c == '}') break;
                throw new RuntimeException("对象中应为 , 或 } @" + pos);
            }
            return m;
        }

        List<Object> array() {
            List<Object> a = new ArrayList<>();
            expect('['); ws();
            if (peek() == ']') { pos++; return a; }
            while (true) {
                a.add(value());
                ws();
                char c = next();
                if (c == ',') continue;
                if (c == ']') break;
                throw new RuntimeException("数组中应为 , 或 ] @" + pos);
            }
            return a;
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            String hex = text.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default: throw new RuntimeException("非法转义 \\" + e);
                    }
                } else sb.append(c);
            }
            return sb.toString();
        }

        Boolean bool() {
            if (text.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (text.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new RuntimeException("非法字面量 @" + pos);
        }
        Object nul() {
            if (text.startsWith("null", pos)) { pos += 4; return null; }
            throw new RuntimeException("非法字面量 @" + pos);
        }
        Object number() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < text.length() && "0123456789+-.eE".indexOf(text.charAt(pos)) >= 0) pos++;
            String n = text.substring(start, pos);
            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
            try { return Long.parseLong(n); } catch (NumberFormatException e) { return Double.parseDouble(n); }
        }

        char peek() { return text.charAt(pos); }
        char next() { if (pos >= text.length()) throw new RuntimeException("意外结尾"); return text.charAt(pos++); }
        void expect(char c) { ws(); char a = next(); if (a != c) throw new RuntimeException("期望 '" + c + "' 但得到 '" + a + "' @" + pos); }
    }

    /* ---------- 类型安全取值 ---------- */
    @SuppressWarnings("unchecked")
    public static Map<String,Object> obj(Object o) { return o instanceof Map ? (Map<String,Object>) o : null; }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> obj(Map<String,Object> m, String k) { return m == null ? null : obj(m.get(k)); }
    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) { return o instanceof List ? (List<Object>) o : null; }
    @SuppressWarnings("unchecked")
    public static List<Object> arr(Map<String,Object> m, String k) { return m == null ? null : arr(m.get(k)); }

    public static String str(Object o) { return o == null ? null : o.toString(); }
    public static String str(Map<String,Object> m, String k) { return m == null ? null : str(m.get(k)); }

    public static Number num(Map<String,Object> m, String k) {
        if (m == null) return null;
        Object o = m.get(k);
        return o instanceof Number ? (Number) o : null;
    }
    public static int intVal(Map<String,Object> m, String k, int dflt) {
        Number n = num(m, k); return n == null ? dflt : n.intValue();
    }
    public static boolean bool(Map<String,Object> m, String k) {
        Object o = m == null ? null : m.get(k);
        return Boolean.TRUE.equals(o);
    }

    /* ---------- 序列化 ---------- */
    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) sb.append("null");
        else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?,?> e : ((Map<?,?>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                write(sb, e.getKey().toString());
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object v : (List<?>) o) { if (!first) sb.append(','); first = false; write(sb, v); }
            sb.append(']');
        } else if (o instanceof Boolean || o instanceof Number) {
            sb.append(o);
        } else {
            sb.append(encode(o.toString()));
        }
    }

    public static String encode(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** 便捷构造对象。 */
    public static Map<String,Object> map(Object... kv) {
        Map<String,Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }
}
