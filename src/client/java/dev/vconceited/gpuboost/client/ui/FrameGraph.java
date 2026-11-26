package dev.vconceited.gpuboost.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.vconceited.gpuboost.core.FrameTimeRing;

public final class FrameGraph {
	private static final int GREEN = 0xFF55DD66;
	private static final int YELLOW = 0xFFFFCC33;
	private static final int RED = 0xFFFF5544;
	private static final int BACKGROUND = 0x90000000;
	private static final int TARGET_LINE = 0xA0FFFFFF;

	private final float[] samples;

	public FrameGraph(int maxWidth) {
		this.samples = new float[maxWidth];
	}

	public void draw(GuiGraphicsExtractor g, Font font, FrameTimeRing ring, int x, int y, int width, int height, double targetFps, boolean labels) {
		int w = Math.min(width, this.samples.length);
		g.fill(x, y, x + w, y + height, BACKGROUND);
		int n = ring.copyRecent(this.samples, w);

		if (n == 0) {
			return;
		}

		double targetMs = 1000.0 / Math.max(1, targetFps);
		float peak = 0;

		for (int k = 0; k < n; k++) {
			peak = Math.max(peak, this.samples[k]);
		}

		double scaleMs = Math.min(100.0, Math.max(targetMs * 2.0, peak * 1.1));
		int start = x + w - n;

		for (int k = 0; k < n; k++) {
			float ms = this.samples[k];
			int bar = (int) Math.max(1, Math.min(height, Math.round(ms / scaleMs * height)));
			int color = ms <= targetMs * 1.05 ? GREEN : ms <= targetMs * 2 ? YELLOW : RED;
			g.fill(start + k, y + height - bar, start + k + 1, y + height, color);
		}

		int ty = y + height - (int) Math.round(targetMs / scaleMs * height);

		if (ty > y && ty < y + height) {
			for (int px = x; px < x + w; px += 4) {
				g.fill(px, ty, Math.min(px + 2, x + w), ty + 1, TARGET_LINE);
			}
		}

		if (labels && font != null) {
			g.text(font, Fmt.num(scaleMs, 0) + " ms", x + 2, y + 2, 0xFFFFFFFF);
			g.text(font, "target " + Fmt.num(targetMs, 1) + " ms", x + 2, y + height - font.lineHeight - 1, 0xFFDDDDDD);
		}
	}
}
