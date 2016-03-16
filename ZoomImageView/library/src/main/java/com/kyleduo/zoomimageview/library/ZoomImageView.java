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
import android.graphics.RectF;
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

import utils.CalcUtils;

/**
 * ZoomImageView
 * <p/>
 * Created by kyle on 16/3/7.
 */
public class ZoomImageView extends View {
	public static final String TAG = "ZoomImageView";

	public static final long ANIM_DURATION = 300;
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

	// scale
	private RectF mTargetRect = new RectF();
	private RectF mLimitRect = new RectF();
	private RectF mCurrentRect = new RectF();

	// image
	private Matrix mMatrix = new Matrix();
	private Matrix mTempMatrix = new Matrix(); // used for calc end value of animation.
	private float[] mInitialValues = new float[9];
	private float[] mCurrentValues = new float[9];
	private float[] mTempValues = new float[9];
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

		setDrawingCacheEnabled(true);
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

	private int getBitmapWidth() {
		return mPresentBitmap.getWidth();
	}

	private int getBitmapHeight() {
		return mPresentBitmap.getHeight();
	}

	private PointF getScalePivot(float scale, PointF pivot, RectF targetRect) {
		updateValue();
		mTempMatrix.set(mMatrix);

		float diffScale = scale / mCurrentValues[Matrix.MSCALE_X];
		if (pivot == null) {
			pivot = new PointF();
		}
		mTempMatrix.postScale(diffScale, diffScale, pivot.x, pivot.y);
		mTempMatrix.getValues(mTempValues);

		float ctx = mCurrentValues[Matrix.MTRANS_X], cty = mCurrentValues[Matrix.MTRANS_Y];
		float ttx = mTempValues[Matrix.MTRANS_X], tty = mTempValues[Matrix.MTRANS_Y];

		RectF limit = getLimitBounds(scale);

		if (ttx < limit.left) {
			ttx = limit.left;
		} else if (ttx > limit.right) {
			ttx = limit.right;
		}

		if (tty < limit.top) {
			tty = limit.top;
		} else if (tty > limit.bottom) {
			tty = limit.bottom;
		}

		if (targetRect != null) {
			targetRect.set(ttx, tty, ttx + getBitmapWidth() * mTempValues[Matrix.MSCALE_X], tty + getBitmapHeight() * mTempValues[Matrix.MSCALE_Y]);
		}

		boolean fix = ttx != mTempValues[Matrix.MTRANS_X] || tty != mTempValues[Matrix.MTRANS_Y];

		if (fix) {
			float cw = getBitmapWidth() * mCurrentValues[Matrix.MSCALE_X];
			float tw = cw * diffScale;
			PointF p = CalcUtils.crossPointOfTwoLines(ctx, cty, ctx + cw, cty, ttx, tty, ttx + tw, tty);
			pivot.set(p);
		}

		return new PointF(pivot.x, pivot.y);
	}

	private RectF getLimitBounds(float scale) {
		float tw = scale * mPresentBitmap.getWidth();
		float th = scale * mPresentBitmap.getHeight();

		float left = tw >= mWidth ? mWidth - tw : (mWidth - tw) / 2;
		float right = tw >= mWidth ? 0 : mWidth - (mWidth - tw) / 2 - tw;
		float top = th >= mHeight ? mHeight - th : (mHeight - th) / 2;
		float bottom = th >= mHeight ? 0 : mHeight - (mHeight - th) / 2 - th;

		mLimitRect.set(left, top, right, bottom);
		return mLimitRect;
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
		int action = event.getActionMasked();
		if (mAnimator != null && mAnimator.isRunning()) {
			Log.d(TAG, "ANIMATING IGNORE");
//			return true;
			if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
				mAnimator.cancel();
			} else {
				return false;
			}
		}

		if (!mScrollerCompat.isFinished()) {
			Log.d(TAG, "ABORT SCROLL");
			mScrollerCompat.abortAnimation();
		}


		float x = event.getX();
		float y = event.getY();

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}

		mVelocityTracker.addMovement(event);

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				Log.d(TAG, "ACTION_DOWN");
				mStartPointF.set(x, y);
				mLastPointF.set(mStartPointF);

				if (mLastUpTime != 0) {
					mTwiceTapInterval = SystemClock.elapsedRealtime() - mLastUpTime;
				} else {
					mTwiceTapInterval = -1;
				}

				break;
			case MotionEvent.ACTION_POINTER_DOWN: {
				Log.d(TAG, "ACTION_POINTER_DOWN");
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
				Log.d(TAG, "ACTION_MOVE");
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
				Log.d(TAG, "ACTION_POINTER_UP");
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
				Log.d(TAG, "ACTION_UP");
			case MotionEvent.ACTION_CANCEL:
				Log.d(TAG, "ACTION_CANCEL");
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
				RectF limit = getLimitBounds();
				mScrollerCompat.fling((int) mCurrentValues[Matrix.MTRANS_X], (int) mCurrentValues[Matrix.MTRANS_Y], (int) vx, (int) vy, (int) limit.left, (int) limit.right, (int) limit.top, (int) limit.bottom);
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
			PointF point = limit(mScrollerCompat.getCurrX(), mScrollerCompat.getCurrY());

			float dx = point.x - mCurrentValues[Matrix.MTRANS_X], dy = point.y - mCurrentValues[Matrix.MTRANS_Y];
			if (point.x != mScrollerCompat.getCurrX() && point.y != mScrollerCompat.getCurrY()) {
				mScrollerCompat.abortAnimation();
				return;
			}

			mMatrix.postTranslate(dx, dy);
			postInvalidate();
		}
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
		mCurrentRect.set(mCurrentValues[Matrix.MTRANS_X],
				mCurrentValues[Matrix.MTRANS_Y],
				getBitmapWidth() * mCurrentValues[Matrix.MSCALE_X],
				getBitmapHeight() * mCurrentValues[Matrix.MSCALE_X]);
	}

	private RectF getLimitBounds() {
		updateValue();
		float tw = mCurrentValues[Matrix.MSCALE_X] * mPresentBitmap.getWidth();
		float th = mCurrentValues[Matrix.MSCALE_Y] * mPresentBitmap.getHeight();

		float left = tw >= mWidth ? mWidth - tw : (mWidth - tw) / 2;
		float right = tw >= mWidth ? 0 : mWidth - (mWidth - tw) / 2 - tw;
		float top = th >= mHeight ? mHeight - th : (mHeight - th) / 2;
		float bottom = th >= mHeight ? 0 : mHeight - (mHeight - th) / 2 - th;

		return new RectF(left, top, right, bottom);
	}

	private PointF limit(float x, float y) {
		RectF limit = getLimitBounds();
		PointF point = new PointF(x, y);

		if (point.x < limit.left) {
			point.x = limit.left;
		} else if (point.x > limit.right) {
			point.x = limit.right;
		}

		if (point.y < limit.top) {
			point.y = limit.top;
		} else if (point.y > limit.bottom) {
			point.y = limit.bottom;
		}
		return point;
	}

	private boolean move(float dx, float dy) {
		updateValue();
		float tx = mCurrentValues[Matrix.MTRANS_X] + dx;
		float ty = mCurrentValues[Matrix.MTRANS_Y] + dy;

		PointF targetPoint = limit(tx, ty);

		dx = targetPoint.x - mCurrentValues[Matrix.MTRANS_X];
		dy = targetPoint.y - mCurrentValues[Matrix.MTRANS_Y];

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
		float ts = currentScale;
		if (currentScale < mMinScale) {
			ts = mMinScale;
		} else if (currentScale > mMaxScale) {
			ts = mMaxScale;
		}

		PointF p = getScalePivot(ts, mScaleCurrentPivot, mTargetRect);

		// no need to animate
		if (mTargetRect.left == mCurrentValues.length && mTargetRect.top == mCurrentRect.top && ts == currentScale) {
			return null;
		}

		ValueAnimator animator = createAnimator(null);

		if (ts == currentScale) {
			// just translation
			animator.addUpdateListener(new LimitBoundsAnimatorListener(mCurrentRect.left, mCurrentRect.top, mTargetRect.left, mTargetRect.top, true));
		} else {
			// when diffScale != 1, scale by (x0, y0) to translate image to target position
			animator.addUpdateListener(new LimitBoundsAnimatorListener(currentScale, ts, p.x, p.y));
			if (ts > currentScale) {
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

	private ValueAnimator buildScaleAnimator(float ex, float ey, ScaleLevel level) {
		updateValue();
		float currentScale = mCurrentValues[Matrix.MSCALE_X];
		float scale = currentScale;

		switch (level) {
			case PTD:
				scale = Math.min(2, getResources().getDisplayMetrics().density);
				break;
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

		if (mCurrentValues[Matrix.MSCALE_X] == scale) {
			return null;
		}

		float px = ex, py = ey;

		PointF p = getScalePivot(scale, new PointF(px, py), null);
		px = p.x;
		py = p.y;

		return createAnimator(new ScaleAnimatorListener(mCurrentValues[Matrix.MSCALE_X], scale, px, py));
	}

	private ValueAnimator createAnimator(ValueAnimator.AnimatorUpdateListener listener) {
		ValueAnimator animator = ValueAnimator.ofInt(0, 100);
		animator.setDuration(ANIM_DURATION);
		if (listener != null) {
			animator.addUpdateListener(listener);
		}
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
				return PTD;
			}
		}, // pixel to pixel
		PTD {
			@Override
			ScaleLevel next() {
				return FIT_IN;
			}
		}; // pixel to dp

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

			if (fraction == 1) {
				Log.w(TAG, "matx: " + mMatrix);
			}

			invalidate();
		}
	}
}
