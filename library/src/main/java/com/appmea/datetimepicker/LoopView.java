package com.appmea.datetimepicker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewParent;
import android.widget.OverScroller;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public class LoopView<T extends LoopItem> extends View {
    // ====================================================================================================================================================================================
    // <editor-fold desc="Constants">

    private static final float PI_HALF   = (float) (Math.PI / 2F);
    private static final float PI        = (float) Math.PI;
    private static final float PI_DOUBLE = (float) (Math.PI * 2F);

    private static final float DEFAULT_TEXT_SIZE = 40;

    private static final int DEFAULT_COLOR_TEXT          = 0XFFAFAFAF;
    private static final int DEFAULT_COLOR_TEXT_SELECTED = 0XFF313131;
    private static final int DEFAULT_COLOR_DIVIDER       = 0XFFC5C5C5;

    private static final int   TOUCH_SLOP       = 8;
    private static final float MINIMUM_VELOCITY = 50;
    private static final float MAXIMUM_VELOCITY = 8000;
    // </editor-fold>


    // ====================================================================================================================================================================================
    // <editor-fold desc="Properties">

    final   ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?>       mFuture;

    /**
     * Ranges from Min: itemHeight/2 TO Max: itemHeight * (displayableItemCount + itemCount -1)
     */
    int          currentScrollY;
    int          minScrollY;
    int          maxScrollY;
    Handler      handler;
    LoopListener loopListener;

    List<T> items;
    private T selectedItem;

    private GestureDetector gestureDetector;

    final Paint    paintText            = new Paint();
    final Paint    paintSelected        = new Paint();
    final Paint    paintDivider         = new Paint();
    final Paint    paintTest            = new Paint();
    final int      displayableItemCount = 3;
    final String[] as                   = new String[displayableItemCount];
    final float[]  ratios               = new float[displayableItemCount];

    final float lineSpacingMultiplier = 3.5F;

    int     initPosition = -1;
    boolean loopEnabled;

    int   textSize;
    int   maxTextWidth;
    int   maxTextHeight;
    int   colorText;
    int   colorTextSelected;
    int   colorDivider;
    float firstLineY;
    float secondLineY;
    int   preCurrentIndex;
    float measuredHeight;
    float halfCircumference;
    float radius;
    float itemHeight;
    float measuredWidth;
    private float           radiantSingleItem;
    private int             lastMotionY;
    private boolean         isBeingDragged;
    private VelocityTracker velocityTracker;
    private OverScroller    scroller;


    // </editor-fold>


    // ====================================================================================================================================================================================
    // <editor-fold desc="Constructor">

    public LoopView(Context context) {
        this(context, null);
    }

    public LoopView(Context context, AttributeSet attributeset) {
        this(context, attributeset, 0);
    }

    public LoopView(Context context, AttributeSet attributeset, int defStyleAttr) {
        super(context, attributeset, defStyleAttr);
        if (isInEditMode()) {
            items = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                items.add((T) new StringLoopItem(String.valueOf(i)));
            }
            initPosition = 3;
        }

        initLoopView(context, attributeset);
    }
    // </editor-fold>


    // ====================================================================================================================================================================================
    // <editor-fold desc="Initialisation">

    private void initLoopView(Context context, AttributeSet attributeset) {
        final TypedArray array = context.obtainStyledAttributes(attributeset, R.styleable.LoopView);
        try {
            textSize = (int) array.getDimension(R.styleable.LoopView_textSize, array.getResources().getDisplayMetrics().density * DEFAULT_TEXT_SIZE);
            colorText = array.getColor(R.styleable.LoopView_textColor, DEFAULT_COLOR_TEXT);
            colorTextSelected = array.getColor(R.styleable.LoopView_selectedTextColor, DEFAULT_COLOR_TEXT_SELECTED);
            colorDivider = array.getColor(R.styleable.LoopView_dividerColor, DEFAULT_COLOR_DIVIDER);
        } finally {
            array.recycle();
            if (textSize == 0) {
                textSize = (int) DEFAULT_TEXT_SIZE;
            }

            if (colorText == 0) {
                colorText = DEFAULT_COLOR_TEXT;
            }

            if (colorTextSelected == 0) {
                colorTextSelected = DEFAULT_COLOR_TEXT_SELECTED;
            }

            if (colorDivider == 0) {
                colorDivider = DEFAULT_COLOR_DIVIDER;
            }
        }

        initPaint(paintText, colorText);
        initPaint(paintSelected, colorTextSelected);
        initPaint(paintDivider, colorDivider);
        initPaint(paintTest, Color.GREEN);

        handler = new MessageHandler(this);
        GestureDetector.SimpleOnGestureListener simpleOnGestureListener = new LoopViewGestureListener(this);
        gestureDetector = new GestureDetector(context, simpleOnGestureListener);
        gestureDetector.setIsLongpressEnabled(false);
        scroller = new OverScroller(context);
    }

    private void initPaint(Paint paint, int color) {
        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.SANS_SERIF);
        paint.setTextSize(textSize);
    }

    private void initData() {
        if (items == null) {
            return;
        }

        measureTextWidthHeight();
        itemHeight = maxTextHeight * lineSpacingMultiplier;
        float angleDegree = 180f / (displayableItemCount - 1);
        // As each item has the same height
        radiantSingleItem = (float) Math.toRadians(angleDegree);

//        Can't use acr, as it would falsify the itemHeight
//        acr for centered item needs to be itemHeight
//        arc = 2*sin(alpha/2)*r
//        radius = (float) (itemHeight / (2 * Math.sin(Math.toRadians(angleDegree / 2))));

        halfCircumference = itemHeight * (displayableItemCount - 1);
        radius = (float) (halfCircumference / Math.PI);
        measuredHeight = radius * 2;

        firstLineY = ((measuredHeight - itemHeight) / 2.0F);
        secondLineY = ((measuredHeight + itemHeight) / 2.0F);
        if (initPosition == -1) {
            if (loopEnabled) {
                initPosition = (items.size() + 1) / 2;
            } else {
                initPosition = 0;
            }
        }

        currentScrollY = (int) ((itemHeight * initPosition + itemHeight / 2f));
        preCurrentIndex = initPosition;

        Timber.e("ItemHeight: %f, Radius: %f, TotalScrollY: %d", itemHeight, radius, currentScrollY);
    }

    private void measureTextWidthHeight() {
        Paint.FontMetrics fm = paintText.getFontMetrics();
        maxTextHeight = (int) (fm.descent - fm.ascent);

        Rect rect = new Rect();
        for (int i = 0; i < items.size(); i++) {
            String string = items.get(i).getText();
            paintSelected.getTextBounds(string, 0, string.length(), rect);
            int textWidth = rect.width();
            if (textWidth > maxTextWidth) {
                maxTextWidth = textWidth;
            }
        }
    }

    private int measureDimension(int desiredSize, int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize;
        } else {
            result = desiredSize;
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }

        if (result < desiredSize) {
            Timber.e("The view is too small, the content might get cut");
        }

        return result;
    }
    // </editor-fold>


    // ====================================================================================================================================================================================
    // <editor-fold desc="Android Lifecycle">

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        initData();

        int desiredWidth = getSuggestedMinimumWidth() + getPaddingLeft() + getPaddingRight();
        int desiredHeight = getSuggestedMinimumHeight() + getPaddingTop() + getPaddingBottom();

        int maxWidth = Math.max(desiredWidth, maxTextWidth);
        int maxHeight = (int) Math.max(desiredHeight, measuredHeight);

        setMeasuredDimension(measureDimension(maxWidth, widthMeasureSpec), measureDimension(maxHeight, heightMeasureSpec));
        measuredWidth = getMeasuredWidth();

        minScrollY = (int) (itemHeight / 2);
        maxScrollY = (int) (itemHeight * (-0.5 + items.size()));
        Timber.e("MinScroll: %d, MaxScroll: %d", minScrollY, maxScrollY);
    }


    Paint  fill = new Paint();
    Random rnd  = new Random();

    private void updateCurrentIndex() {
        preCurrentIndex = (int) (currentScrollY / itemHeight);
        sanityCheckIndex();
        getCurrentCanvasItems();
    }

    private void sanityCheckIndex() {
        if (!loopEnabled) {
            if (preCurrentIndex < 0) {
                preCurrentIndex = 0;
            }
            if (preCurrentIndex > items.size() - 1) {
                preCurrentIndex = items.size() - 1;
            }
        } else {
            if (preCurrentIndex < 0) {
                preCurrentIndex = items.size() + preCurrentIndex;
            }
            if (preCurrentIndex > items.size() - 1) {
                preCurrentIndex = preCurrentIndex - items.size();
            }
        }
    }

    private void getCurrentCanvasItems() {
        int k1 = 0;
        while (k1 < displayableItemCount) {
            int l1 = preCurrentIndex - (displayableItemCount / 2 - k1);
            if (loopEnabled) {
                if (l1 < 0) {
                    l1 = l1 + items.size();
                }
                if (l1 > items.size() - 1) {
                    l1 = l1 - items.size();
                }
                as[k1] = items.get(l1).getText();
            } else if (l1 < 0) {
                as[k1] = "";
            } else if (l1 > items.size() - 1) {
                as[k1] = "";
            } else {
                as[k1] = items.get(l1).getText();
            }
            k1++;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (items == null) {
            super.onDraw(canvas);
            return;
        }

        int scrollOffset = (int) (currentScrollY % itemHeight);
        updateCurrentIndex();

        int left = (int) ((measuredWidth - maxTextWidth) / 2);
        int j1 = 0;


        for (int i = 0; i < displayableItemCount; i++) {
            float radiantItemHeight = (float) ((itemHeight * (i + 1) - scrollOffset) / radius);
            float radiantBottom = radiantItemHeight - radiantSingleItem;

            if (radiantBottom > PI && radiantItemHeight < PI_DOUBLE) {
                // Item outside
            } else if (radiantItemHeight < PI_HALF) {
                if (radiantBottom >= 0) {
                    // Item completely on right side
                    ratios[i] = (float) (1f - Math.cos(radiantItemHeight) - (1F - Math.cos(radiantBottom)));
                } else {
                    // Item partially on right side
                    ratios[i] = (float) (1f - Math.cos(radiantItemHeight));
                }

            } else {
                if (radiantBottom < PI_HALF) {
                    // Item on both sides
                    ratios[i] = (float) (Math.abs(Math.cos(radiantItemHeight)) + Math.cos(radiantBottom));

                } else if (radiantItemHeight <= PI) {
                    // Item completely on left side
                    ratios[i] = (float) (-1f - Math.cos(radiantItemHeight) - (-1F - Math.cos(radiantBottom)));

                } else {
                    // Item partially on left side
                    ratios[i] = (float) (1f + Math.cos(radiantBottom));
                }
            }

            ratios[i] = ratios[i] * radius / itemHeight;
//            Timber.e("String: %s, RadItem: %f, RadBottom: %f, Ratio: %f", as[i], radiantItemHeight, radiantBottom, ratios[i]);
        }

//        paintTest.setAlpha(50);
//        canvas.drawPaint(paintTest);

        int translation = 0;
        while (j1 < displayableItemCount) {
            canvas.save();
            canvas.translate(0.0F, translation);
            translation += itemHeight * ratios[j1];
            canvas.scale(1.0F, ratios[j1]);

            fill.setStyle(Paint.Style.FILL_AND_STROKE);
//            Beautiful
//            if (as[j1].equals("")) {
//                if (j1 % 2 == 0) {
//                    fill.setARGB(255, 255, 0, 255);
//                } else {
//                    fill.setARGB(255, 255, 255, 0);
//                }
//            } else {
//                if (Integer.valueOf(as[j1]) % 2 == 0) {
//                    fill.setARGB(255, 255, 255, 255);
//                } else {
//                    fill.setARGB(255, 0, 0, 0);
//                }
//            }

//          Functional
            if (j1 % 2 == 0) {
                fill.setARGB(255, 255, 255, 255);
            } else {
                fill.setARGB(255, 0, 0, 0);
            }


            canvas.clipRect(0, 0, measuredWidth, itemHeight);
            canvas.drawPaint(fill);
            int yPos = (int) ((itemHeight / 2) - ((paintText.descent() + paintText.ascent()) / 2));
            canvas.drawText(as[j1], left, yPos, paintText);

            canvas.restore();
            j1++;
        }
        // </editor-fold>

        super.onDraw(canvas);
    }
    // </editor-fold>


    // ====================================================================================================================================================================================
    // <editor-fold desc="Interfaces">

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent motionevent) {
        initVelocityTrackerIfNotExists();

        switch (motionevent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (getChildCount() == 0) {
                    return false;
                }

                initOrResetVelocityTracker();
                velocityTracker.addMovement(motionevent);

                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                removeCallbacks(settle);
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }

                // Remember where the motion event started
                lastMotionY = (int) motionevent.getY();
                break;


            case MotionEvent.ACTION_MOVE:
                velocityTracker.addMovement(motionevent);

                final int y = (int) motionevent.getY();
                int deltaY = lastMotionY - y;


                if (!isBeingDragged && Math.abs(deltaY) > TOUCH_SLOP) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    isBeingDragged = true;
                    if (deltaY > 0) {
                        deltaY -= TOUCH_SLOP;
                    } else {
                        deltaY += TOUCH_SLOP;
                    }
                }

                if (isBeingDragged) {
                    // Scroll to follow the motion event
                    lastMotionY = y;
                    scrollOrSpringBack(deltaY);
                    postInvalidateOnAnimation();
                }
                break;

            case MotionEvent.ACTION_UP:
                velocityTracker.addMovement(motionevent);
                final VelocityTracker finalVelocityTracker = velocityTracker;
                finalVelocityTracker.computeCurrentVelocity(1000, MAXIMUM_VELOCITY);
                int initialVelocity = (int) finalVelocityTracker.getYVelocity();
                Timber.e("Velocity: %d", initialVelocity);

                if (isBeingDragged) {
                    if ((Math.abs(initialVelocity) > MINIMUM_VELOCITY)) {
                        Timber.e("Start FLING");
                        // Start fling
//                        scroller.fling(0, getScrollY(), 0, initialVelocity, 0, getWidth(), minScrollY, maxScrollY);
//                        flingWithNestedDispatch(-initialVelocity);
//                    } else if (mScroller.springBack(mScrollX, mScrollY, 0, 0, 0, getScrollRange())) {
                        post(settle);
                    } else {
                        Timber.e("Start SETTLE");
                        post(settle);
                    }

                    endDrag();
                }
                break;
        }
        return true;
    }
    // </editor-fold>


    private Runnable settle = new Runnable() {
        @Override
        public void run() {
            int deltaY = (int) ((currentScrollY - minScrollY) % itemHeight);
            if (deltaY > itemHeight / 2) {
                deltaY = (int) (itemHeight - deltaY);
            } else {
                deltaY = -deltaY;
            }

            int tenth = (int) (itemHeight * 0.1);
            boolean finished = true;
            int realDelta;

            // If deltaY negative => need to scroll up
            if (deltaY < 0) {
                realDelta = Math.max(-tenth, deltaY);
                if (realDelta < -tenth) {
                    finished = false;
                }
            } else {
                realDelta = Math.min(tenth, deltaY);
                if (realDelta <= tenth) {
                    finished = false;
                }
            }

            scrollOrSpringBack(realDelta);
            postInvalidateOnAnimation();

            if (!finished) {
                postDelayed(this, 700);
            }
        }
    };


    // ====================================================================================================================================================================================
    // <editor-fold desc="Methods">


    private void scrollOrSpringBack(int deltaY) {
        int newY = currentScrollY + deltaY;
        if (isWithinScrollRange(newY)) {
            currentScrollY += deltaY;
        } else {
            springBack(newY);
        }

    }

    private void springBack(int newY) {
        if (newY <= minScrollY) {
            currentScrollY = minScrollY;
        } else if (newY >= maxScrollY) {
            currentScrollY = maxScrollY;
        }
    }

    private void endDrag() {
        isBeingDragged = false;
        recycleVelocityTracker();
    }

    private void initOrResetVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void settlePosition() {
        int offset = (int) ((currentScrollY - minScrollY) % itemHeight);
        Timber.e("smoothScroll: %d", offset);

        cancelFuture();
        mFuture = mExecutor.scheduleWithFixedDelay(new MTimer(this, offset), 0, 10, TimeUnit.MILLISECONDS);
    }

    protected final void smoothScroll(float velocityY) {
        cancelFuture();
        int velocityFling = 20;
        mFuture = mExecutor.scheduleWithFixedDelay(new LoopTimerTask(this, velocityY), 0, velocityFling, TimeUnit.MILLISECONDS);
    }

    protected final void itemSelected() {
        if (loopListener != null) {
            loopListener.onItemSelect(selectedItem);
        }
    }

    @Nullable
    private T findItem(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        for (T item : items) {
            if (item.getText().equals(text)) {
                return item;
            }
        }

        return null;
    }
    // </editor-fold>


    // ====================================================================================================================================================================================
    // <editor-fold desc="Getter & Setter">

    public boolean isWithinScrollRange(int newY) {
        return newY > minScrollY && newY < maxScrollY;
    }

    /**
     * Returns the maximum scroll range
     * <br>
     * First and last item will always be ONLY half visible.
     *
     * @return Maximum scroll range
     */
    public int getScrollRange() {
        return (int) (itemHeight * (getChildCountWithPlaceholder() - 1) - getHeight());
    }

    /**
     * Returns the child count without the placeholder count
     *
     * @return Number of children excl. placeholder
     */
    public int getChildCount() {
        return items != null ? items.size() : 0;
    }

    /**
     * Returns the child count including the placeholder count for non-looped views
     *
     * @return Number of children incl. placeholder
     */
    public int getChildCountWithPlaceholder() {
        return getChildCount() + (loopEnabled ? 0 : displayableItemCount / 2);
    }

    public void setItems(List<T> items) {
        this.items = items;
        if (items != null) {
            if (!items.isEmpty()) {
                this.initPosition = Math.min(items.size() - 1, initPosition);
                Timber.e("setItems: %d", initPosition);
            }
        }
        initData();
        invalidate();
    }

    @Nullable
    public T getItem(int position) {
        if (items == null || position < 0 || position > items.size() - 1) {
            return null;
        }

        return items.get(position);
    }

    public void setLoopEnabled(boolean enabled) {
        loopEnabled = enabled;
    }

    public final void setListener(LoopListener LoopListener) {
        loopListener = LoopListener;
    }
    // </editor-fold>


    /**
     * öalskdjfö jasöldkfj ölasd
     */

    static void settlePosition(LoopView loopview) {
        loopview.settlePosition();
    }

    public void cancelFuture() {
        if (mFuture != null && !mFuture.isCancelled()) {
            mFuture.cancel(true);
            mFuture = null;
        }
    }

    public void setTextSize(int textSize) {
        if (this.textSize != textSize) {
            this.textSize = textSize;
            initPaint(paintText, colorText);
            initPaint(paintSelected, colorTextSelected);
            initPaint(paintDivider, colorDivider);
            requestLayout();
        }
    }

    public final void setInitPosition(int initPosition) {
        if (items != null) {
            if (items.isEmpty()) {
                return;
            }

            this.initPosition = Math.min(items.size() - 1, initPosition);
            return;
        }

        this.initPosition = initPosition;
        invalidate();
    }


}
