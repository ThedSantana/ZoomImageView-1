package utils;

/**
 * Created by kyle on 16/3/7.
 */
public class BoundsF {
	private float left, top, right, bottom;

	public BoundsF(float left, float top, float right, float bottom) {
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BoundsF boundsF = (BoundsF) o;

		if (Float.compare(boundsF.left, left) != 0) return false;
		if (Float.compare(boundsF.top, top) != 0) return false;
		if (Float.compare(boundsF.right, right) != 0) return false;
		return Float.compare(boundsF.bottom, bottom) == 0;

	}

	@Override
	public int hashCode() {
		int result = (left != +0.0f ? Float.floatToIntBits(left) : 0);
		result = 31 * result + (top != +0.0f ? Float.floatToIntBits(top) : 0);
		result = 31 * result + (right != +0.0f ? Float.floatToIntBits(right) : 0);
		result = 31 * result + (bottom != +0.0f ? Float.floatToIntBits(bottom) : 0);
		return result;
	}

	public float getLeft() {
		return left;
	}

	public void setLeft(float left) {
		this.left = left;
	}

	public float getTop() {
		return top;
	}

	public void setTop(float top) {
		this.top = top;
	}

	public float getRight() {
		return right;
	}

	public void setRight(float right) {
		this.right = right;
	}

	public float getBottom() {
		return bottom;
	}

	public void setBottom(float bottom) {
		this.bottom = bottom;
	}
}
