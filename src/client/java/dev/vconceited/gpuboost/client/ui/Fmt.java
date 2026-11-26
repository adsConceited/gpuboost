package dev.vconceited.gpuboost.client.ui;

import java.util.Locale;

public final class Fmt {
	private Fmt() {
	}

	public static String num(double v, int digits) {
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			return "n/a";
		}

		return String.format(Locale.ROOT, "%." + digits + "f", v);
	}

	public static String pct(double v) {
		return Double.isNaN(v) ? "n/a" : String.format(Locale.ROOT, "%.0f%%", v);
	}

	public static String mb(double v) {
		return Double.isNaN(v) ? "n/a" : String.format(Locale.ROOT, "%.0f MB", v);
	}

	public static String onOff(boolean b) {
		return b ? "ON" : "OFF";
	}
}
