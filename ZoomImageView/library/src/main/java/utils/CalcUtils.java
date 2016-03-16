package utils;

import android.graphics.PointF;

/**
 * Created by kyle on 16/3/15.
 */
public class CalcUtils {

	/**
	 *
	 * Cross of line from (x1, y1) to (x2, y2) by line from (m1, n1) to (m2, n2)
	 *
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @param m1
	 * @param n1
	 * @param m2
	 * @param n2
	 *
	 * @return cross point in PointF
	 */
	public static PointF crossPointOfTwoLines(float x1, float y1, float x2, float y2, float m1, float n1, float m2, float n2) {
		float a1 = 0, b1 = 0, a2 = 0, b2 = 0;
		float x0 = 0, y0 = 0;

		if (m1 == x1) {
			x0 = x1;
		} else {
			a1 = (n1 - y1) / (m1 - x1);
			b1 = n1 - a1 * m1;
		}

		if (m2 == x2) {
			x0 = x2;
		} else {
			a2 = (n2 - y2) / (m2 - x2);
			b2 = n2 - a2 * m2;
		}

		if (a1 != a2 && m1 != x1 && m2 != x2) {
			x0 = (b1 - b2) / (a2 - a1);
		}

		if (a1 == 0) {
			y0 = a2 * x0 + b2;
		} else {
			y0 = a1 * x0 + b1;
		}

		return new PointF(x0, y0);
	}

}
