package dev.vconceited.gpuboost.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MiniJsonTest {
	@Test
	void roundTripsNestedStructures() {
		Map<String, Object> inner = new LinkedHashMap<>();
		inner.put("a", 1);
		inner.put("b", "two \"quoted\" \\ back\nslash");
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("num", 3.25);
		root.put("int", 42);
		root.put("flag", true);
		root.put("nothing", null);
		root.put("list", List.of(1, "x", false));
		root.put("inner", inner);
		root.put("objects", List.of(Map.of("k", 1)));

		Map<String, Object> parsed = MiniJson.parseObject(MiniJson.write(root));

		assertEquals(3.25, (Double) parsed.get("num"), 1e-12);
		assertEquals(42.0, (Double) parsed.get("int"), 1e-12);
		assertEquals(Boolean.TRUE, parsed.get("flag"));
		assertTrue(parsed.containsKey("nothing"));
		assertEquals(null, parsed.get("nothing"));
		assertEquals(List.of(1.0, "x", false), parsed.get("list"));
		assertEquals("two \"quoted\" \\ back\nslash", MiniJson.getObject(parsed, "inner").get("b"));
	}

	@Test
	void parsesUnicodeEscapesAndExponents() {
		Map<String, Object> m = MiniJson.parseObject("{\"s\":\"\\u00e9\\t\",\"e\":-1.5e3}");
		assertEquals("é\t", m.get("s"));
		assertEquals(-1500.0, (Double) m.get("e"), 1e-9);
	}

	@Test
	void rejectsMalformedInput() {
		assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\":1,}"));
		assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\" 1}"));
		assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("[1,2"));
		assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("\"unterminated"));
		assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{} trailing"));
		assertThrows(IllegalArgumentException.class, () -> MiniJson.parseObject("[1]"));
	}

	@Test
	void writesNanAsNull() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("x", Double.NaN);
		assertTrue(MiniJson.write(m).contains("\"x\": null"));
	}

	@Test
	void typedAccessorsFallBack() {
		Map<String, Object> m = MiniJson.parseObject("{\"i\":7.6,\"s\":5,\"b\":\"yes\",\"l\":[\"a\",1,\"b\"]}");
		assertEquals(8, MiniJson.getInt(m, "i", 0));
		assertEquals("dflt", MiniJson.getString(m, "s", "dflt"));
		assertEquals(false, MiniJson.getBool(m, "b", false));
		assertEquals(List.of("a", "b"), MiniJson.getStringList(m, "l", List.of()));
		assertEquals(3, MiniJson.getInt(m, "missing", 3));
	}
}
