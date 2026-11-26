package dev.vconceited.gpuboost.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MiniJson {
	private MiniJson() {
	}

	public static Object parse(String text) {
		Parser p = new Parser(text);
		p.skipWhitespace();
		Object value = p.readValue(0);
		p.skipWhitespace();

		if (!p.atEnd()) {
			throw p.error("Unexpected trailing characters");
		}

		return value;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parseObject(String text) {
		Object value = parse(text);

		if (!(value instanceof Map)) {
			throw new IllegalArgumentException("JSON root is not an object");
		}

		return (Map<String, Object>) value;
	}

	private static final class Parser {
		private static final int MAX_DEPTH = 64;
		private final String s;
		private int i;

		Parser(String s) {
			this.s = s;
		}

		boolean atEnd() {
			return this.i >= this.s.length();
		}

		IllegalArgumentException error(String msg) {
			return new IllegalArgumentException(msg + " at offset " + this.i);
		}

		void skipWhitespace() {
			while (this.i < this.s.length()) {
				char c = this.s.charAt(this.i);

				if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
					this.i++;
				} else {
					break;
				}
			}
		}

		Object readValue(int depth) {
			if (depth > MAX_DEPTH) {
				throw error("Nesting too deep");
			}

			if (atEnd()) {
				throw error("Unexpected end of input");
			}

			char c = this.s.charAt(this.i);

			return switch (c) {
				case '{' -> readObject(depth);
				case '[' -> readArray(depth);
				case '"' -> readString();
				case 't' -> readLiteral("true", Boolean.TRUE);
				case 'f' -> readLiteral("false", Boolean.FALSE);
				case 'n' -> readLiteral("null", null);
				default -> {
					if (c == '-' || (c >= '0' && c <= '9')) {
						yield readNumber();
					}

					throw error("Unexpected character '" + c + "'");
				}
			};
		}

		Object readLiteral(String literal, Object value) {
			if (!this.s.startsWith(literal, this.i)) {
				throw error("Invalid literal");
			}

			this.i += literal.length();
			return value;
		}

		Map<String, Object> readObject(int depth) {
			Map<String, Object> map = new LinkedHashMap<>();
			this.i++;
			skipWhitespace();

			if (!atEnd() && this.s.charAt(this.i) == '}') {
				this.i++;
				return map;
			}

			while (true) {
				skipWhitespace();

				if (atEnd() || this.s.charAt(this.i) != '"') {
					throw error("Expected object key");
				}

				String key = readString();
				skipWhitespace();

				if (atEnd() || this.s.charAt(this.i) != ':') {
					throw error("Expected ':'");
				}

				this.i++;
				skipWhitespace();
				map.put(key, readValue(depth + 1));
				skipWhitespace();

				if (atEnd()) {
					throw error("Unterminated object");
				}

				char c = this.s.charAt(this.i++);

				if (c == '}') {
					return map;
				}

				if (c != ',') {
					throw error("Expected ',' or '}'");
				}
			}
		}

		List<Object> readArray(int depth) {
			List<Object> list = new ArrayList<>();
			this.i++;
			skipWhitespace();

			if (!atEnd() && this.s.charAt(this.i) == ']') {
				this.i++;
				return list;
			}

			while (true) {
				skipWhitespace();
				list.add(readValue(depth + 1));
				skipWhitespace();

				if (atEnd()) {
					throw error("Unterminated array");
				}

				char c = this.s.charAt(this.i++);

				if (c == ']') {
					return list;
				}

				if (c != ',') {
					throw error("Expected ',' or ']'");
				}
			}
		}

		String readString() {
			StringBuilder sb = new StringBuilder();
			this.i++;

			while (true) {
				if (atEnd()) {
					throw error("Unterminated string");
				}

				char c = this.s.charAt(this.i++);

				if (c == '"') {
					return sb.toString();
				}

				if (c == '\\') {
					if (atEnd()) {
						throw error("Unterminated escape");
					}

					char e = this.s.charAt(this.i++);

					switch (e) {
						case '"' -> sb.append('"');
						case '\\' -> sb.append('\\');
						case '/' -> sb.append('/');
						case 'b' -> sb.append('\b');
						case 'f' -> sb.append('\f');
						case 'n' -> sb.append('\n');
						case 'r' -> sb.append('\r');
						case 't' -> sb.append('\t');
						case 'u' -> {
							if (this.i + 4 > this.s.length()) {
								throw error("Bad unicode escape");
							}

							try {
								sb.append((char) Integer.parseInt(this.s.substring(this.i, this.i + 4), 16));
							} catch (NumberFormatException ex) {
								throw error("Bad unicode escape");
							}

							this.i += 4;
						}
						default -> throw error("Bad escape '\\" + e + "'");
					}
				} else {
					sb.append(c);
				}
			}
		}

		Double readNumber() {
			int start = this.i;

			if (this.s.charAt(this.i) == '-') {
				this.i++;
			}

			while (!atEnd()) {
				char c = this.s.charAt(this.i);

				if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
					this.i++;
				} else {
					break;
				}
			}

			try {
				return Double.parseDouble(this.s.substring(start, this.i));
			} catch (NumberFormatException ex) {
				throw error("Bad number");
			}
		}
	}

	public static String write(Object value) {
		StringBuilder sb = new StringBuilder();
		writeValue(sb, value, 0);
		sb.append('\n');
		return sb.toString();
	}

	private static void indent(StringBuilder sb, int level) {
		for (int k = 0; k < level; k++) {
			sb.append("  ");
		}
	}

	private static void writeValue(StringBuilder sb, Object value, int level) {
		if (value == null) {
			sb.append("null");
		} else if (value instanceof String str) {
			writeString(sb, str);
		} else if (value instanceof Boolean b) {
			sb.append(b.booleanValue());
		} else if (value instanceof Number n) {
			writeNumber(sb, n);
		} else if (value instanceof Map<?, ?> map) {
			if (map.isEmpty()) {
				sb.append("{}");
				return;
			}

			sb.append("{\n");
			int idx = 0;

			for (Map.Entry<?, ?> e : map.entrySet()) {
				indent(sb, level + 1);
				writeString(sb, String.valueOf(e.getKey()));
				sb.append(": ");
				writeValue(sb, e.getValue(), level + 1);

				if (++idx < map.size()) {
					sb.append(',');
				}

				sb.append('\n');
			}

			indent(sb, level);
			sb.append('}');
		} else if (value instanceof Iterable<?> it) {
			List<Object> items = new ArrayList<>();
			it.forEach(items::add);

			if (items.isEmpty()) {
				sb.append("[]");
				return;
			}

			boolean simple = items.stream().allMatch(o -> o == null || o instanceof Number || o instanceof Boolean || o instanceof String);

			if (simple) {
				sb.append('[');

				for (int k = 0; k < items.size(); k++) {
					if (k > 0) {
						sb.append(", ");
					}

					writeValue(sb, items.get(k), level + 1);
				}

				sb.append(']');
				return;
			}

			sb.append("[\n");

			for (int k = 0; k < items.size(); k++) {
				indent(sb, level + 1);
				writeValue(sb, items.get(k), level + 1);

				if (k + 1 < items.size()) {
					sb.append(',');
				}

				sb.append('\n');
			}

			indent(sb, level);
			sb.append(']');
		} else if (value instanceof Enum<?> en) {
			writeString(sb, en.name());
		} else {
			writeString(sb, value.toString());
		}
	}

	private static void writeNumber(StringBuilder sb, Number n) {
		if (n instanceof Double || n instanceof Float) {
			double d = n.doubleValue();

			if (Double.isNaN(d) || Double.isInfinite(d)) {
				sb.append("null");
			} else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
				sb.append((long) d);
			} else {
				sb.append(d);
			}
		} else {
			sb.append(n.longValue());
		}
	}

	private static void writeString(StringBuilder sb, String s) {
		sb.append('"');

		for (int k = 0; k < s.length(); k++) {
			char c = s.charAt(k);

			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}

		sb.append('"');
	}

	public static double getDouble(Map<String, Object> map, String key, double fallback) {
		Object v = map.get(key);
		return v instanceof Number n ? n.doubleValue() : fallback;
	}

	public static int getInt(Map<String, Object> map, String key, int fallback) {
		Object v = map.get(key);
		return v instanceof Number n ? (int) Math.round(n.doubleValue()) : fallback;
	}

	public static boolean getBool(Map<String, Object> map, String key, boolean fallback) {
		Object v = map.get(key);
		return v instanceof Boolean b ? b : fallback;
	}

	public static String getString(Map<String, Object> map, String key, String fallback) {
		Object v = map.get(key);
		return v instanceof String s ? s : fallback;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> getObject(Map<String, Object> map, String key) {
		Object v = map.get(key);
		return v instanceof Map ? (Map<String, Object>) v : null;
	}

	public static List<String> getStringList(Map<String, Object> map, String key, List<String> fallback) {
		Object v = map.get(key);

		if (!(v instanceof List<?> list)) {
			return fallback;
		}

		List<String> out = new ArrayList<>();

		for (Object o : list) {
			if (o instanceof String s) {
				out.add(s);
			}
		}

		return out;
	}
}
