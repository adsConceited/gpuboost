package dev.vconceited.gpuboost.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class Reflect {
	private Reflect() {
	}

	public static Method findMethod(Class<?> type, String name, Class<?>... params) {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name, params);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException | RuntimeException ignored) {
			}
		}

		try {
			return type.getMethod(name, params);
		} catch (NoSuchMethodException | RuntimeException e) {
			return null;
		}
	}

	public static Field findField(Class<?> type, String name) {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException | RuntimeException ignored) {
			}
		}

		return null;
	}

	public static Object call(Object target, String name) {
		if (target == null) {
			return null;
		}

		Method m = findMethod(target.getClass(), name);

		if (m == null) {
			return null;
		}

		try {
			return m.invoke(target);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	public static Object callStatic(String className, String method) {
		try {
			Class<?> c = Class.forName(className, true, Reflect.class.getClassLoader());
			Method m = c.getMethod(method);
			return m.invoke(null);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	public static Object readStaticField(String className, String field) {
		try {
			Class<?> c = Class.forName(className, true, Reflect.class.getClassLoader());
			Field f = findField(c, field);
			return f != null && Modifier.isStatic(f.getModifiers()) ? f.get(null) : null;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	public static boolean classExists(String className) {
		try {
			Class.forName(className, false, Reflect.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	public static final class FieldReader {
		private final String name;
		private Class<?> resolvedFor;
		private Field field;

		public FieldReader(String name) {
			this.name = name;
		}

		public Object read(Object target) {
			if (target == null) {
				return null;
			}

			Class<?> c = target.getClass();

			if (c != this.resolvedFor) {
				this.resolvedFor = c;
				this.field = findField(c, this.name);
			}

			if (this.field == null) {
				return null;
			}

			try {
				return this.field.get(target);
			} catch (IllegalAccessException | RuntimeException e) {
				return null;
			}
		}
	}

	public static int intCall(Object target, String name, int fallback) {
		Object v = call(target, name);
		return v instanceof Number n ? n.intValue() : fallback;
	}

	public static Map<String, Object> describe(Object obj, int depth) {
		Map<String, Object> out = new LinkedHashMap<>();

		if (obj == null || depth > 4) {
			return out;
		}

		Class<?> c = obj.getClass();

		if (c.isRecord()) {
			for (RecordComponent rc : c.getRecordComponents()) {
				try {
					Method acc = rc.getAccessor();
					acc.setAccessible(true);
					out.put(rc.getName(), simplify(acc.invoke(obj), depth));
				} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
				}
			}

			return out;
		}

		for (Method m : c.getMethods()) {
			if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers()) || m.getDeclaringClass() == Object.class) {
				continue;
			}

			Class<?> rt = m.getReturnType();

			if (rt.isPrimitive() || rt == String.class || Number.class.isAssignableFrom(rt) || rt == Boolean.class) {
				if (rt == void.class) {
					continue;
				}

				try {
					out.put(m.getName(), simplify(m.invoke(obj), depth));
				} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
				}
			}
		}

		return out;
	}

	private static Object simplify(Object v, int depth) {
		if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean) {
			return v;
		}

		if (v instanceof Enum<?> e) {
			return e.name();
		}

		if (v instanceof Collection<?> col) {
			Set<String> s = new LinkedHashSet<>();

			for (Object o : col) {
				s.add(String.valueOf(o));
			}

			return s;
		}

		if (v.getClass().isRecord()) {
			return describe(v, depth + 1);
		}

		return v.toString();
	}
}
