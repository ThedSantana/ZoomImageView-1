package com.kyleduo.zoomimageview.library;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.FloatEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.view.ViewConfigurationCompat;
import android.support.v4.widget.ScrollerCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import utils.BoundsF;

/**
 * ZoomImageView
 * <p/>
 * Created by kyle on 16/3/7.
 */
public class ZoomImageView extends View {
	public static final String TAG = "ZoomImageView";

	public static final long ANIM_DURATION = 200;
	public static final float MAX_SCALE = 4;
	public static final float OVER_SCALE = 1f;

	private Bitmap mBitmap;
	private Bitmap mPresentBitmap;

	private int mWidth, mHeight;

	// touch event
	private PointF mStartPointF = new PointF();
	private PointF mLastPointF = new PointF();
	private int mTapTimeout, mDoubleTapTimeout;
	private int mTouchSlop;
	private long mLastUpTime = 0, mTwiceTapInterval = -1;
	private PointF mScaleCurrentPivot = new PointF();
	private PointF mScaleLastPivot = new PointF();
	private float mScaleStartDistance;
	private float mPinchLastScale;
	private int mTouchFirstPointId, mTouchSecondPointId;

	// control
	private ScaleLevel mScaleLevel = ScaleLevel.USER;
	private ScaleLevel mTempScaleLevel; // used for storing temp scale whose scale value equals to it's previous level;
	private boolean mPinching;
	private float mCurrentScale = 1;
	private float mMinScale = 1, mMaxScale = 1;
	private float mOverScale = 0;

	// image
	private Matrix mMatrix = new Matrix();
	private float[] mInitialValues = new float[9];
	private float[] mCurrentValues = new float[9];
	private float mCurrentImageWidth, mCurrentImageHeight;
	private ValueAnimator mAnimator;

	// handler
	private Handler mHandler = new Handler();
	private Runnable mClickCallback = new Runnable() {
		@Override
		public void run() {
			performClick();
		}
	};

	private ScrollerCompat mScrollerCompat;
	private VelocityTracker mVelocityTracker;

	// temp
	private PointF mTempPointF = new PointF();
	private PointF mTempPointF1 = new PointF();
	private PointF mTempPointF2 = new PointF();

	public ZoomImageView(Context context) {
		super(context);
		init();
	}

	public ZoomImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		mTapTimeout = ViewConfiguration.getTapTimeout();
		mDoubleTapTimeout = ViewConfiguration.getDoubleTapTimeout();
		mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(ViewConfiguration.get(getContext())); // equals 48 in Nexus 5, it's too large.
		mTouchSlop /= 2;

		mScrollerCompat = ScrollerCompat.create(getContext(), new DecelerateInterpolator());

		mMaxScale = MAX_SCALE;
		mOverScale = OVER_SCALE;

	}

	public void setBitmap(Bitmap bitmap) {
		if (bitmap == null || bitmap.isRecycled()) {
			return;
		}
		mBitmap = bitmap;
		if (mWidth == 0 || mHeight == 0) {
			return;
		}
		showPresentBitmap(mBitmap);
	}

	public Bitmap getBitmap() {
		return mBitmap;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (w != oldw || h != oldh) {
			mWidth = w;
			mHeight = h;
			if (mWidth != 0 && mHeight != 0 && mPresentBitmap == null && mBitmap != null) {
				showPresentBitmap(mBitmap);
			}
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (mPresentBitmap != null) {
			canvas.drawBitmap(mPresentBitmap, mMatrix, null);

//			updateValue();

//			Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
//			p.setColor(Color.RED);
//			p.setStrokeWidth(2);
//			canvas.drawCircle(mScaleCurrentPivot.x, mScaleCurrentPivot.y, 12, p);
//			canvas.drawLine(mScaleCurrentPivot.x, mCurrentValues[Matrix.MTRANS_Y], mScaleCurrentPivot.x, mCurrentValues[Matrix.MTRANS_Y] + mPresentBitmap.getHeight() * mCurrentValues[Matrix.MSCALE_X], p);
//			canvas.drawLine(mCurrentValues[Matrix.MTRANS_X], mScaleCurrentPivot.y, mCurrentValues[Matrix.MTRANS_X] + mPresentBitmap.getWidth() * mCurrentValues[Matrix.MSCALE_X], mScaleCurrentPivot.y, p);
//			p.setColor(Color.BLUE);
//			canvas.drawCircle(mTempPointF.x, mTempPointF.y, 12, p);
//			canvas.drawLine(mTempPointF.x, mTempPointF.y, mTempPointF1.x, mTempPointF1.y, p);
//			canvas.drawLine(mTempPointF.x, mTempPointF.y, mTempPointF2.x, mTempPointF2.y, p);
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if (mScrollerCompat != null && !mScrollerCompat.isFinished()) {
			mScrollerCompat.abortAnimation();
		}
		if (mAnimator != null && mAnimator.isRunning()) {
			mAnimator.cancel();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mAnimator != null && mAnimator.isRunning()) {
			return true;
		}

		if (!mScrollerCompat.isFinished()) {
			mScrollerCompat.abortAnimation();
		}

		int action = event.getActionMasked();

		float x = event.getX();
		float y = event.getY();

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}

		mVelocityTracker.addMovement(event);

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				mStartPointF.set(x, y);
				mLastPointF.set(mStartPointF);

				if (mLastUpTime != 0) {
					mTwiceTapInterval = SystemClock.elapsedRealtime() - mLastUpTime;
				} else {
					mTwiceTapInterval = -1;
				}

				break;
			case MotionEvent.ACTION_POINTER_DOWN: {
				int pointCount = event.getPointerCount();
				if (pointCount == 2) {
					mPinching = true;
					mTouchFirstPointId = event.getPointerId(0);
					mTouchSecondPointId = event.getPointerId(1);
					float firstX = event.getX(0);
					float firstY = event.getY(0);
					float secondX = event.getX(1);
					float secondY = event.getY(1);
					mScaleStartDistance = distance(firstX, firstY, secondX, secondY);
					mScaleCurrentPivot.set((secondX + firstX) / 2, (secondY + firstY) / 2);
					mScaleLastPivot.set(mScaleCurrentPivot);
					mPinchLastScale = 1;
				}
			}
			break;

			case MotionEvent.ACTION_MOVE: {
				int index0 = event.findPointerIndex(mTouchFirstPointId);
				int index1 = event.findPointerIndex(mTouchSecondPointId);
				int pointCount = event.getPointerCount();
				if (pointCount == 1 || index0 < 0 || index1 < 0) {
					int index = index0 >= 0 ? index0 : index1;
					if (index < 0) {
						break;
					}
					if (mLastPointF.x == -1 && mLastPointF.y == -1) {
						mLastPointF.set(event.getX(index), event.getY(index));
						break;
					}
					move(x - mLastPointF.x, y - mLastPointF.y);
					mLastPointF.set(event.getX(index), event.getY(index));
				} else if (pointCount > 1 && index0 >= 0 && index1 >= 0) {
					float firstX = event.getX(index0);
					float firstY = event.getY(index0);
					float secondX = event.getX(index1);
					float secondY = event.getY(index1);
					mScaleCurrentPivot.set((secondX + firstX) / 2, (secondY + firstY) / 2);
					float distance = distance(firstX, firstY, secondX, secondY);
					float scale = distance / mScaleStartDistance;
					float deltaScale = scale / mPinchLastScale;
					scale(deltaScale);
					mScaleLastPivot.set(mScaleCurrentPivot);
					mPinchLastScale = scale;
				}
			}
			break;
			case MotionEvent.ACTION_POINTER_UP:
				// leave just one
				int id = event.getPointerId(event.getActionIndex());
				if (mPinching && (id == mTouchFirstPointId || id == mTouchSecondPointId)) {
					mPinching = false;
					mLastPointF.set(-1, -1);
					limitBounds();
					return false;
				}
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				mLastPointF.set(-1, -1);
				if (!mPinching && event.getEventTime() - event.getDownTime() < mTapTimeout && Math.abs(x - mStartPointF.x) < mTouchSlop && Math.abs(y - mStartPointF.y) < mTouchSlop) {
					// tap
					if (mTwiceTapInterval == -1 || mTwiceTapInterval > mDoubleTapTimeout) {
						// single tap
						Log.d("ss", "tap");
						mLastUpTime = SystemClock.elapsedRealtime();
						mHandler.postDelayed(mClickCallback, mDoubleTapTimeout);
					} else {
						// double tap
						Log.d("ss", "double tap");
						mHandler.removeCallbacks(mClickCallback);
						mLastUpTime = 0;
						doubleTap(x, y);
					}
					break;
				} else {
					mPinching = false;
					limitBounds();
				}
				mVelocityTracker.computeCurrentVelocity(1000);
				float vx = mVelocityTracker.getXVelocity();
				float vy = mVelocityTracker.getYVelocity();
				BoundsF boundsF = getLimitBounds();
				mScrollerCompat.fling((int) mCurrentValues[Matrix.MTRANS_X], (int) mCurrentValues[Matrix.MTRANS_Y], (int) vx, (int) vy, (int) boundsF.getLeft(), (int) boundsF.getRight(), (int) boundsF.getTop(), (int) boundsF.getBottom());
				mVelocityTracker.clear();
				mVelocityTracker.recycle();
				mVelocityTracker = null;

				break;
		}
		return true;
	}

	@Override
	public void computeScroll() {
		if (mScrollerCompat.computeScrollOffset()) {
			Log.d(TAG, "computeScroll: " + mScrollerCompat.getCurrX() + "  y: " + mScrollerCompat.getCurrY());
			BoundsF boundsF = getLimitBounds();
			float x = mScrollerCompat.getCurrX(), y = mScrollerCompat.getCurrY();

			boolean limitX = false, limitY = false;

			if (x < boundsF.getLeft()) {
				x = boundsF.getLeft();
				limitX = true;
			} else if (x > boundsF.getRight()) {
				x = boundsF.getRight();
				limitX = true;
			}

			if (y < boundsF.getTop()) {
				y = boundsF.getTop();
				limitY = true;
			} else if (y > boundsF.getBottom()) {
				y = boundsF.getBottom();
				limitY = true;
			}

			float dx = x - mCurrentValues[Matrix.MTRANS_X], dy = y - mCurrentValues[Matrix.MTRANS_Y];
			if (limitX && limitY) {
				mScrollerCompat.abortAnimation();
				return;
			}

			mMatrix.postTranslate(dx, dy);
		}
		postInvalidate();
	}

	private float distance(float x1, float y1, float x2, float y2) {
		return (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
	}

	/**
	 * Update current value
	 */
	private void updateValue() {
		if (mMatrix == null) {
			return;
		}
		mMatrix.getValues(mCurrentValues);
	}

	private BoundsF getLimitBounds() {
		updateValue();
		float tw = mCurrentValues[Matrix.MSCALE_X] * mPresentBitmap.getWidth();
		float th = mCurrentValues[Matrix.MSCALE_Y] * mPresentBitmap.getHeight();

		float left = tw >= mWidth ? mWidth - tw : (mWidth - tw) / 2;
		float right = tw >= mWidth ? 0 : mWidth - (mWidth - tw) / 2 - tw;
		float top = th >= mHeight ? mHeight - th : (mHeight - th) / 2;
		float bottom = th >= mHeight ? 0 : mHeight - (mHeight - th) / 2 - th;

		return new BoundsF(left, top, right, bottom);
	}

	private boolean move(float dx, float dy) {
		BoundsF boundsF = getLimitBounds();
		float tx = mCurrentValues[Matrix.MTRANS_X] + dx;
		float ty = mCurrentValues[Matrix.MTRANS_Y] + dy;


		if (tx < boundsF.getLeft()) {
			dx = boundsF.getLeft() - mCurrentValues[Matrix.MTRANS_X];
		} else if (tx > boundsF.getRight()) {
			dx = boundsF.getRight() - mCurrentValues[Matrix.MTRANS_X];
		}

		if (ty < boundsF.getTop()) {
			dy = boundsF.getTop() - mCurrentValues[Matrix.MTRANS_Y];
		} else if (ty > boundsF.getBottom()) {
			dy = boundsF.getBottom() - mCurrentValues[Matrix.MTRANS_Y];
		}

		if (dx == 0 && dy == 0) {
			return false;
		}

		mMatrix.postTranslate(dx, dy);
		invalidate();
		return true;
	}

	private void scale(float ds) {
		mScaleLevel = ScaleLevel.USER;
		updateValue();
		float currentScale = mCurrentValues[Matrix.MSCALE_X];
		float ts = currentScale * ds;
		if (ts > mMaxScale + mOverScale) {
			ds = (mMaxScale + mOverScale) / currentScale;
		}/* else if (ts > mMaxScale) {
			ds = 1 + (ds - 1) / 2;
		} */ else if (ts < mMinScale - mOverScale) {
			ds = (mMinScale - mOverScale) / currentScale;
		}/* else if (ts < mMinScale) {
			ds = 1 + (ds - 1) / 2;
		}*/
		// scale 1x, just keep what it is.
		if (ds == 1) {
			return;
		}
		mMatrix.postScale(ds, ds, mScaleCurrentPivot.x, mScaleCurrentPivot.y);
		float dx = mScaleCurrentPivot.x - mScaleLastPivot.x;
		float dy = mScaleCurrentPivot.y - mScaleLastPivot.y;
		// drag the image follow the pivot point.
		mMatrix.postTranslate(dx, dy);
		invalidate();
	}

	/// control

	private void limitBounds() {
		if (mAnimator != null && mAnimator.isRunning()) {
			mAnimator.cancel();
		}

		// 应该处理边界碰撞和center位置，自动还原。
		mAnimator = buildLimitBoundsAnimator();
		if (mAnimator != null) {
			mAnimator.start();
		}
	}

	private void doubleTap(float ex, float ey) {
		if (mAnimator != null && mAnimator.isRunning()) {
			mAnimator.cancel();
		}
		mScaleLevel = mScaleLevel.next();
		mAnimator = buildScaleAnimator(ex, ey, mScaleLevel);
		if (mAnimator != null) {
			mAnimator.start();
		}
	}

	/// animator builder
	public ValueAnimator buildLimitBoundsAnimator() {
		updateValue();

		float currentScale = mCurrentValues[Matrix.MSCALE_X];
		float diffScale = 1;
		float ts = currentScale;
		if (currentScale < mMinScale) {
			ts = mMinScale;
		} else if (currentScale > mMaxScale) {
			ts = mMaxScale;
		}
		diffScale = ts / currentScale;

		float cl = mCurrentValues[Matrix.MTRANS_X];
		float ct = mCurrentValues[Matrix.MTRANS_Y];
		float cw = mCurrentValues[Matrix.MSCALE_X] * mPresentBitmap.getWidth();

		float tw = cw * diffScale;
		float th = mCurrentValues[Matrix.MSCALE_Y] * mPresentBitmap.getHeight() * diffScale;

		float tl = diffScale >= 1 ? cl * diffScale : mWidth / 2 - (mWidth / 2 - cl) * diffScale;
		float tt = diffScale >= 1 ? ct * diffScale : mWidth / 2 - (mWidth / 2 - ct) * diffScale;

		float left = tw >= mWidth ? mWidth - tw : (mWidth - tw) / 2;
		float right = tw >= mWidth ? 0 : mWidth - (mWidth - tw) / 2 - tw;
		float top = th >= mHeight ? mHeight - th : (mHeight - th) / 2;
		float bottom = th >= mHeight ? 0 : mHeight - (mHeight - th) / 2 - th;

		float tx = tl, ty = tt;

		if (tl < left) {
			tx = left;
		} else if (tl > right) {
			tx = right;
		}

		if (tt < top) {
			ty = top;
		} else if (tt > bottom) {
			ty = bottom;
		}

		// no need to animate
		if (tl == tx && tt == ty && diffScale == 1) {
			return null;
		}

		ValueAnimator animator = ValueAnimator.ofInt(0, 100);
		animator.setDuration(ANIM_DURATION);

		if (diffScale == 1) {
			// just translation
			animator.addUpdateListener(new LimitBoundsAnimatorListener(cl, ct, tx, ty, true));
		} else {
			// when diffScale != 1, scale by (x0, y0) to translate image to target position
			PointF p = crossPointOfTwoLines(cl, ct, cl + cw, ct, tx, ty, tx + tw, ty);

			animator.addUpdateListener(new LimitBoundsAnimatorListener(currentScale, ts, p.x, p.y));
			if (diffScale > 1) {
				animator.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						super.onAnimationEnd(animation);
						mScaleLevel = ScaleLevel.FIT_IN;
					}
				});
			}
		}
		return animator;
	}

	private PointF crossPointOfTwoLines(float x1, float y1, float x2, float y2, float m1, float n1, float m2, float n2) {
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

		if (x0 != x1 && x0 != x2) {
			if (a2 == a1) {
				x0 = 0;
			} else {
				x0 = (b1 - b2) / (a2 - a1);
			}
		}

		if (x0 == x1) {
			y0 = a2 * x0 + b2;
		} else {
			y0 = a1 * x0 + b1;
		}
		return new PointF(x0, y0);
	}

	public void testMove() {
		mMatrix.postScale(0.5f, 0.5f, 0, 0);
		mMatrix.postTranslate(50, 0);
	}

	private ValueAnimator buildScaleAnimator(float ex, float ey, ScaleLevel level) {
		updateValue();
		float currentScale = mCurrentValues[Matrix.MSCALE_X];
		float scale = currentScale;

		switch (level) {
			case PTP:
				scale = 1;
				break;
			case FIT_IN:
				scale = Math.min(mWidth * 1.0f / mPresentBitmap.getWidth(), mHeight * 1.0f / mPresentBitmap.getHeight());
				scale = Math.min(scale, mMaxScale);
				scale = Math.max(scale, mMinScale);
				break;
			case FIT_OUT:
				scale = Math.max(mWidth * 1.0f / mPresentBitmap.getWidth(), mHeight * 1.0f / mPresentBitmap.getHeight());
				scale = Math.min(scale, mMaxScale);
				scale = Math.max(scale, mMinScale);
				break;
		}

		Log.d(TAG, "current scale: " + currentScale + "  scale: " + scale);
		if (Math.abs(scale - currentScale) < 0.01) { // forbid double ex
			ScaleLevel next = level.next();
			if (mTempScaleLevel == null) {
				mTempScaleLevel = level;
			} else if (mTempScaleLevel == next) {
				return null;
			}
			return buildScaleAnimator(ex, ey, next);
		}
		mTempScaleLevel = null;

		ValueAnimator.AnimatorUpdateListener listener = new ScaleAnimatorListener(mCurrentValues[Matrix.MSCALE_X], scale, ex, ey);

		if (mCurrentValues[Matrix.MSCALE_X] == scale) {
			return null;
		}

		ValueAnimator animator = ValueAnimator.ofInt(0, 100);
		animator.setDuration(ANIM_DURATION);
		animator.addUpdateListener(listener);

		return animator;
	}

	private void showPresentBitmap(Bitmap bitmap) {
		float MAX = 4096;
		float ratio = Math.min(MAX / mBitmap.getWidth(), MAX / mBitmap.getHeight());
		if (ratio < 1) {
			// need create scaled bitmap
			mPresentBitmap = Bitmap.createScaledBitmap(mBitmap, (int) (mBitmap.getWidth() * ratio), (int) (mBitmap.getHeight() * ratio), true);
		} else {
			mPresentBitmap = bitmap;
		}
		mScaleLevel = ScaleLevel.FIT_IN;
		float scale = Math.min((float) mWidth / mPresentBitmap.getWidth(), (float) mHeight / mPresentBitmap.getHeight());
		mMinScale = Math.min(scale, 1);
		mMatrix.postTranslate(mWidth / 2 - mPresentBitmap.getWidth() * scale / 2, mHeight / 2 - mPresentBitmap.getHeight() * scale / 2);
		mMatrix.preScale(scale, scale);
		mCurrentScale = scale;
		invalidate();
	}

	private enum ScaleLevel {
		USER {
			@Override
			ScaleLevel next() {
				return FIT_IN;
			}
		}, // scaled by user
		FIT_IN {
			@Override
			ScaleLevel next() {
				return FIT_OUT;
			}
		}, // fit view inside, center inside
		FIT_OUT {
			@Override
			ScaleLevel next() {
				return PTP;
			}
		}, // fit view max scaled, center crop
		PTP {
			@Override
			ScaleLevel next() {
				return FIT_IN;
			}
		}; // pixel to pixel

		abstract ScaleLevel next();
	}

	private static class ReleaseEvaluator extends FloatEvaluator {
		@Override
		public Float evaluate(float fraction, Number startValue, Number endValue) {
			float f = 2 * fraction - fraction * fraction;
			return super.evaluate(f, startValue, endValue);
		}
	}

	private class LimitBoundsAnimatorListener implements ValueAnimator.AnimatorUpdateListener {

		private float mStartTransX, mStartTransY;
		private float mTargetTransX, mTargetTransY;
		private float mStartScale, mTargetScale;
		private float mPx, mPy;

		private float mLastTransX, mLastTransY, mLastScale;
		private boolean mTranslation; // true for need to translation

		public LimitBoundsAnimatorListener(float startTransX, float startTransY, float targetTransX, float targetTransY, boolean translation) {
			mStartTransX = startTransX;
			mStartTransY = startTransY;
			mTargetTransX = targetTransX;
			mTargetTransY = targetTransY;

			mLastTransX = mStartTransX;
			mLastTransY = mStartTransY;
			this.mTranslation = translation;
		}

		public LimitBoundsAnimatorListener(float startScale, float targetScale, float px, float py) {
			mStartScale = startScale;
			mTargetScale = targetScale;
			this.mPx = px;
			this.mPy = py;

			this.mTranslation = false;

			mLastScale = mStartScale;
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {

			float transX, transY, scale;

			ReleaseEvaluator evaluator = new ReleaseEvaluator();
			float fraction = ((int) animation.getAnimatedValue()) / 100f;

			if (mTranslation) {
				transX = evaluator.evaluate(fraction, mStartTransX, mTargetTransX);
				transY = evaluator.evaluate(fraction, mStartTransY, mTargetTransY);
				float dx = transX - mLastTransX;
				float dy = transY - mLastTransY;

				mMatrix.postTranslate(dx, dy);
				mLastTransX = transX;
				mLastTransY = transY;
			} else {
				scale = evaluator.evaluate(fraction, mStartScale, mTargetScale);
				float ds = scale / mLastScale;

				mMatrix.postScale(ds, ds, this.mPx, this.mPy);
				mLastScale = scale;
			}

			invalidate();
		}
	}

	private class ScaleAnimatorListener implements ValueAnimator.AnimatorUpdateListener {

		private float mStartScale;
		private float mTargetScale;

		private float mpx = -1, mpy = -1;
		private float mLastScale;

		public ScaleAnimatorListener(float startScale, float targetScale, float mpx, float mpy) {
			mStartScale = startScale;
			mTargetScale = targetScale;
			mLastScale = mStartScale;
			this.mpx = mpx;
			this.mpy = mpy;
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			float scale = -1;

			ReleaseEvaluator evaluator = new ReleaseEvaluator();
			float fraction = ((int) animation.getAnimatedValue()) / 100f;

			scale = evaluator.evaluate(fraction, mStartScale, mTargetScale);

			float ds = scale / mLastScale;

			if (mpx == -1 || mpy == -1) {
				mMatrix.postScale(ds, ds);
			} else {
				mMatrix.postScale(ds, ds, mpx, mpy);
			}

			mLastScale = scale;
			mCurrentScale = scale;
			move(0, 0);
		}
	}
}
