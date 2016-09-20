package cn.hzw.graffiti;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Administrator on 2016/9/3.
 */
public class GraffitiView extends View {

    public static final int ERROR_INIT = -1;
    public static final int ERROR_SAVE = -2;

    private static final float VALUE = 1f;
    private final int TIME_SPAN = 80;

    private HandWrite.GraffitiListener mGraffitiListener;

    private Bitmap mBitmap;
    private Bitmap mGraffitiBitmap;
    private Canvas mBitmapCanvas;

    private float mPrivateScale; // 图片适应屏幕时的缩放倍数
    private int mPrivateHeight, mPrivateWidth;// 图片适应屏幕时的大小
    private float mCentreTranX, mCentreTranY;// 图片居中时的偏移

    private BitmapShader mBitmapShader; // 用于涂鸦的图片上
    private BitmapShader mBitmapShader4C;
    private Path mCurrPath; // 当前手写的路径
    private Path mCanvasPath; //
    private CopyLocation mCopyLocation;

    private Paint mPaint;
    private int mTouchMode;
    private float mPaintSize;
    private int mColor;
    private float mScale;
    private float mTransX = 0, mTransY = 0;

    private boolean mIsPainting = false; // 是否正在绘制
    private boolean isJustDrawOriginal; // 是否只绘制原图


    // 保存涂鸦操作，便于撤销
    private CopyOnWriteArrayList<GraffitiPath> mPathStack = new CopyOnWriteArrayList<GraffitiPath>();
    private CopyOnWriteArrayList<GraffitiPath> pathStackBackup = new CopyOnWriteArrayList<GraffitiPath>();

    /**
     * 画笔
     */
    public enum Pen {
        HAND, // 手绘
        COPY, // 仿制
        ERASER // 橡皮擦
    }

    /**
     * 图形
     */
    public enum Shape {
        HAND_WRITE, //
        ARROW, // 箭头
        LINE, // 直线
        FILL_CIRCLE, // 实心圆
        HOLLOW_CIRCLE, // 空心圆
        FILL_RECT, // 实心矩形
        HOLLOW_RECT, // 空心矩形
    }

    private Pen mPen;
    private Shape mShape;

    private float mTouchDownX, mTouchDownY, mLastTouchX, mLastTouchY, mTouchX, mTouchY;
    private Matrix mShaderMatrix;

    public GraffitiView(Context context, Bitmap bitmap, HandWrite.GraffitiListener listener) {
        super(context);
        mBitmap = bitmap;
        mGraffitiListener = listener;
        if (mGraffitiListener == null) {
            throw new RuntimeException("GraffitiListener is null!!!");
        }
        if (mBitmap == null) {
            throw new RuntimeException("Bitmap is null!!!");
        }

        init();
    }

    public void init() {

        mScale = 1f;
        mPaintSize = 30;
        mColor = Color.RED;
        mPaint = new Paint();
        mPaint.setStrokeWidth(mPaintSize);
        mPaint.setColor(mColor);
        mPaint.setAntiAlias(true);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);// 圆滑

        mPen = Pen.COPY;
        mShape = Shape.HAND_WRITE;

        this.mBitmapShader = new BitmapShader(this.mBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        this.mBitmapShader4C = new BitmapShader(this.mBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

        mShaderMatrix = new Matrix();
        mCanvasPath = new Path();
        mCopyLocation = new CopyLocation(150, 150);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setBG();
        mCopyLocation.updateLocation(toX4C(w / 2), toY4C(h / 2));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mTouchMode = 1;
                mTouchDownX = mTouchX = mLastTouchX = event.getX();
                mTouchDownY = mTouchY = mLastTouchY = event.getY();

                mTouchX += VALUE; // 为了仅点击时也能出现绘图，模拟滑动一个像素点
                mTouchY += VALUE;

                if (mPathStack.size() > 3) {// 当前栈大于3，则拷贝到备份栈
                    pathStackBackup.addAll(mPathStack);
                    mPathStack.clear();
                }

                if (mPen == Pen.COPY && mCopyLocation.isInIt(toX4C(mTouchX), toY4C(mTouchY))) { // 点击copy
                    mCopyLocation.isRelocating = true;
                    mCopyLocation.isCopying = false;
                } else {
                    if (mPen == Pen.COPY) {
                        if (!mCopyLocation.isCopying) {
                            mCopyLocation.setStartPosition(toX4C(mTouchX), toY4C(mTouchY));
                            resetMatrix();
                        }
                        mCopyLocation.isCopying = true;
                    }
                    mCopyLocation.isRelocating = false;
                    if (mShape == Shape.HAND_WRITE) { // 手写
                        mCurrPath = new Path();
                        mCurrPath.moveTo(toX(mTouchDownX), toY(mTouchDownY));
                        mCanvasPath.reset();
                        mCanvasPath.moveTo(toX4C(mTouchDownX), toY4C(mTouchDownY));

                        // 为了仅点击时也能出现绘图，必须移动path
                        mCanvasPath.quadTo(
                                toX4C(mLastTouchX),
                                toY4C(mLastTouchY),
                                toX4C((mTouchX + mLastTouchX) / 2),
                                toY4C((mTouchY + mLastTouchY) / 2));
                    } else {  // 画图形

                    }
                    mIsPainting = true;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchMode = 0;
                mLastTouchX = mTouchX;
                mLastTouchY = mTouchY;
                mTouchX = event.getX();
                mTouchY = event.getY();

                if (mCopyLocation.isRelocating) { // 正在定位location
                    mCopyLocation.updateLocation(toX4C(mTouchX), toY4C(mTouchY));
                    mCopyLocation.isRelocating = false;
                } else {
                    if (mIsPainting) {

                        if (mPen == Pen.COPY) {
                            mCopyLocation.updateLocation(mCopyLocation.mCopyStartX + toX4C(mTouchX) - mCopyLocation.mTouchStartX,
                                    mCopyLocation.mCopyStartY + toY4C(mTouchY) - mCopyLocation.mTouchStartY);
                        }

                        // 把操作记录到加入的堆栈中
                        if (mShape == Shape.HAND_WRITE) { // 手写
                            mCurrPath.quadTo(
                                    toX(mLastTouchX),
                                    toY(mLastTouchY),
                                    toX((mTouchX + mLastTouchX) / 2),
                                    toY((mTouchY + mLastTouchY) / 2));
                            mPathStack.add(GraffitiPath.toPath(mPen, mShape, mPaintSize, mColor, mCurrPath, mPen == Pen.COPY ? new Matrix(mShaderMatrix) : null));
                        } else {  // 画图形
                            mPathStack.add(GraffitiPath.toShape(mPen, mShape, mPaintSize, mColor,
                                    toX(mTouchDownX), toY(mTouchDownY), toX(mTouchX), toY(mTouchY),
                                    mPen == Pen.COPY ? new Matrix(mShaderMatrix) : null));
                        }
                        draw(mBitmapCanvas, mPathStack, false); // 保存到图片中
                        mIsPainting = false;
                    }
                }

                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mTouchMode < 2) { // 单点滑动
                    mLastTouchX = mTouchX;
                    mLastTouchY = mTouchY;
                    mTouchX = event.getX();
                    mTouchY = event.getY();

                    if (mCopyLocation.isRelocating) { // 正在定位location
                        mCopyLocation.updateLocation(toX4C(mTouchX), toY4C(mTouchY));
                    } else {
                        if (mPen == Pen.COPY) {
                            mCopyLocation.updateLocation(mCopyLocation.mCopyStartX + toX4C(mTouchX) - mCopyLocation.mTouchStartX,
                                    mCopyLocation.mCopyStartY + toY4C(mTouchY) - mCopyLocation.mTouchStartY);
                        }
                        if (mShape == Shape.HAND_WRITE) { // 手写
                            mCurrPath.quadTo(
                                    toX(mLastTouchX),
                                    toY(mLastTouchY),
                                    toX((mTouchX + mLastTouchX) / 2),
                                    toY((mTouchY + mLastTouchY) / 2));
                            mCanvasPath.quadTo(
                                    toX4C(mLastTouchX),
                                    toY4C(mLastTouchY),
                                    toX4C((mTouchX + mLastTouchX) / 2),
                                    toY4C((mTouchY + mLastTouchY) / 2));
                        } else { // 画图形

                        }
                    }
                } else { // 多点

                }

                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                mTouchMode -= 1;

                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                mTouchMode += 1;

                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }


    private void setBG() {// 不用resize preview
        int w = mBitmap.getWidth();
        int h = mBitmap.getHeight();
        float nw = w * 1f / getWidth();
        float nh = h * 1f / getHeight();
        if (nw > nh) {
            mPrivateScale = 1 / nw;
            mPrivateWidth = getWidth();
            mPrivateHeight = (int) (h * mPrivateScale);
        } else {
            mPrivateScale = 1 / nh;
            mPrivateWidth = (int) (w * mPrivateScale);
            mPrivateHeight = getHeight();
        }
        // 使图片居中
        mCentreTranX = (getWidth() - mPrivateWidth) / 2f;
        mCentreTranY = (getHeight() - mPrivateHeight) / 2f;

        initCanvas();
        resetMatrix();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap.isRecycled() || mGraffitiBitmap.isRecycled()) {
            return;
        }

        canvas.scale(mPrivateScale * mScale, mPrivateScale * mScale);
        if (isJustDrawOriginal) { // 只绘制原图
            canvas.drawBitmap(mBitmap, (mCentreTranX + mTransX) / (mPrivateScale * mScale), (mCentreTranY + mTransY) / (mPrivateScale * mScale), null);
            return;
        }

        // 绘制涂鸦
        canvas.drawBitmap(mGraffitiBitmap, (mCentreTranX + mTransX) / (mPrivateScale * mScale), (mCentreTranY + mTransY) / (mPrivateScale * mScale), null);

        if (mIsPainting) {  //画在view的画布上
            // 画触摸的路径
            mPaint.setStrokeWidth(mPaintSize);
            mPaint.setColor(mColor);
            if (mShape == Shape.HAND_WRITE) { // 手写
                draw(canvas, mPen, mPaint, mCanvasPath, null, true);
            } else {  // 画图形
                draw(canvas, mPen, mShape, mPaint,
                        toX4C(mTouchDownX), toY4C(mTouchDownY), toX4C(mTouchX), toY4C(mTouchY), null, true);
            }
        }

        if (mPen == Pen.COPY) {
            mCopyLocation.drawItSelf(canvas);
        }

    }

    private void draw(Canvas canvas, Pen pen, Paint paint, Path path, Matrix matrix, boolean is4Canvas) {
        resetPaint(pen, paint, is4Canvas, matrix);

        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, paint);

    }

    private void draw(Canvas canvas, Pen pen, Shape shape, Paint paint, float sx, float sy, float dx, float dy, Matrix matrix, boolean is4Canvas) {
        resetPaint(pen, paint, is4Canvas, matrix);

        paint.setStyle(Paint.Style.STROKE);

        switch (shape) { // 绘制图形
            case ARROW:
                paint.setStyle(Paint.Style.FILL);
                DrawUtil.drawArrow(canvas, sx, sy, dx, dy, paint);
                break;
            case LINE:
                DrawUtil.drawLine(canvas, sx, sy, dx, dy, paint);
                break;
            case FILL_CIRCLE:
                paint.setStyle(Paint.Style.FILL);
            case HOLLOW_CIRCLE:
                DrawUtil.drawCircle(canvas, sx, sy,
                        (float) Math.sqrt((sx - dx) * (sx - dx) + (sy - dy) * (sy - dy)), paint);
                break;
            case FILL_RECT:
                paint.setStyle(Paint.Style.FILL);
            case HOLLOW_RECT:
                DrawUtil.drawRect(canvas, sx, sy, dx, dy, paint);
                break;
            default:
                throw new RuntimeException("unknown shape:" + shape);
        }
    }


    private void draw(Canvas canvas, CopyOnWriteArrayList<GraffitiPath> pathStack, boolean is4Canvas) {
        // 还原堆栈中的记录的操作
        for (GraffitiPath path : pathStack) {
            mPaint.setStrokeWidth(path.mStrokeWidth);
            mPaint.setColor(path.mColor);
            if (path.mShape == Shape.HAND_WRITE) { // 手写
                draw(canvas, path.mPen, mPaint, path.mPath, path.mMatrix, is4Canvas);
            } else { // 画图形
                draw(canvas, path.mPen, path.mShape, mPaint,
                        path.mSx, path.mSy, path.mDx, path.mDy, path.mMatrix, is4Canvas);
            }
        }
    }

    private void resetPaint(Pen pen, Paint paint, boolean is4Canvas, Matrix matrix) {
        switch (pen) { // 设置画笔
            case HAND:
                paint.setShader(null);
                break;
            case COPY:
                if (is4Canvas) { // 画在view的画布上
                    paint.setShader(this.mBitmapShader4C);
                } else { // 调整copy图片位置
                    mBitmapShader.setLocalMatrix(matrix);
                    paint.setShader(this.mBitmapShader);
                }
                break;
            case ERASER:
                if (is4Canvas) {
                    paint.setShader(this.mBitmapShader4C);
                } else {
                    mBitmapShader.setLocalMatrix(null);
                    paint.setShader(this.mBitmapShader);
                }
                break;
        }
    }


    /**
     * 将屏幕触摸坐标x转换成在图片中的坐标
     */
    private float toX(float x) {
        return (x - mCentreTranX - mTransX) / (mPrivateScale * mScale);
    }

    /**
     * 将屏幕触摸坐标y转换成在图片中的坐标
     */
    private float toY(float y) {
        return (y - mCentreTranY - mTransY) / (mPrivateScale * mScale);
    }

    /**
     * 将屏幕触摸坐标x转换成在canvas中的坐标
     */
    private float toX4C(float x) {
        return (x) / (mPrivateScale * mScale);
    }

    /**
     * 将屏幕触摸坐标y转换成在canvas中的坐标
     */
    private float toY4C(float y) {
        return (y) / (mPrivateScale * mScale);
    }

    private static class GraffitiPath {
        Pen mPen;
        Shape mShape;
        float mStrokeWidth;
        int mColor;
        Path mPath;
        float mSx, mSy, mDx, mDy;
        Matrix mMatrix;

        static GraffitiPath toShape(Pen pen, Shape shape, float width, int color,
                                    float sx, float sy, float dx, float dy, Matrix matrix) {
            GraffitiPath path = new GraffitiPath();
            path.mPen = pen;
            path.mShape = shape;
            path.mStrokeWidth = width;
            path.mColor = color;
            path.mSx = sx;
            path.mSy = sy;
            path.mDx = dx;
            path.mDy = dy;
            path.mMatrix = matrix;
            return path;
        }

        static GraffitiPath toPath(Pen pen, Shape shape, float width, int color, Path p, Matrix matrix) {
            GraffitiPath path = new GraffitiPath();
            path.mPen = pen;
            path.mShape = shape;
            path.mStrokeWidth = width;
            path.mColor = color;
            path.mPath = p;
            path.mMatrix = matrix;
            return path;
        }
    }

    private void initCanvas() {
        if (mGraffitiBitmap != null) {
            mGraffitiBitmap.recycle();
        }
        mGraffitiBitmap = mBitmap.copy(Bitmap.Config.RGB_565, true);
        mBitmapCanvas = new Canvas(mGraffitiBitmap);
    }

    private void resetMatrix() {
        if (mPen == Pen.COPY) { // 仿制，加上mCopyLocation记录的偏移

            this.mShaderMatrix.set(null);
            this.mShaderMatrix.postTranslate((mCentreTranX + mTransX) / (mPrivateScale * mScale) + mCopyLocation.mTouchStartX - mCopyLocation.mCopyStartX,
                    (mCentreTranY + mTransY) / (mPrivateScale * mScale) + mCopyLocation.mTouchStartY - mCopyLocation.mCopyStartY);
            this.mBitmapShader4C.setLocalMatrix(this.mShaderMatrix);


            this.mShaderMatrix.set(null);
            this.mShaderMatrix.postTranslate(mCopyLocation.mTouchStartX - mCopyLocation.mCopyStartX, mCopyLocation.mTouchStartY - mCopyLocation.mCopyStartY);
            this.mBitmapShader.setLocalMatrix(this.mShaderMatrix);

        } else {
            this.mShaderMatrix.set(null);
            this.mShaderMatrix.postTranslate((mCentreTranX + mTransX) / (mPrivateScale * mScale), (mCentreTranY + mTransY) / (mPrivateScale * mScale));
            this.mBitmapShader4C.setLocalMatrix(this.mShaderMatrix);


            this.mShaderMatrix.set(null);
            this.mBitmapShader.setLocalMatrix(this.mShaderMatrix);
        }
    }

    /**
     * 调整图片位置
     */
    private void judgePosition() {
        boolean changed = false;
        if (mScale > 1) { // 当图片放大时，图片偏移的位置不能超过屏幕边缘
            if (mTransX > 0) {
                mTransX = 0;
                changed = true;
            } else if (mTransX + mPrivateWidth * mScale < mPrivateWidth) {
                mTransX = mPrivateWidth - mPrivateWidth * mScale;
                changed = true;
            }
            if (mTransY > 0) {
                mTransY = 0;
                changed = true;
            } else if (mTransY + mPrivateHeight * mScale < mPrivateHeight) {
                mTransY = mPrivateHeight - mPrivateHeight * mScale;
                changed = true;
            }
        } else { // 当图片缩小时，图片只能在屏幕可见范围内移动
            if (mTransX + mBitmap.getWidth() * mPrivateScale * mScale > mPrivateWidth) { // mScale<1是preview.width不用乘scale
                mTransX = mPrivateWidth - mBitmap.getWidth() * mPrivateScale * mScale;
                changed = true;
            } else if (mTransX < 0) {
                mTransX = 0;
                changed = true;
            }
            if (mTransY + mBitmap.getHeight() * mPrivateScale * mScale > mPrivateHeight) {
                mTransY = mPrivateHeight - mBitmap.getHeight() * mPrivateScale * mScale;
                changed = true;
            } else if (mTransY < 0) {
                mTransY = 0;
                changed = true;
            }
        }
        if (changed) {
            resetMatrix();
        }
    }

    private class CopyLocation {

        private float mCopyStartX, mCopyStartY; // 仿制的坐标
        private float mTouchStartX, mTouchStartY; // 开始触摸的坐标
        private float mX, mY; // 当前位置

        private Paint mPaint;

        private boolean isRelocating = true; // 正在定位中
        private boolean isCopying = false; // 正在仿制绘图中

        public CopyLocation(float x, float y) {
            mX = x;
            mY = y;
            mTouchStartX = x;
            mTouchStartY = y;
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setStrokeWidth(mPaintSize);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
        }


        public void updateLocation(float x, float y) {
            mX = x;
            mY = y;
        }

        public void setStartPosition(float x, float y) {
            mCopyStartX = mX;
            mCopyStartY = mY;
            mTouchStartX = x;
            mTouchStartY = y;
        }

        public void drawItSelf(Canvas canvas) {
            mPaint.setStrokeWidth(mPaintSize / 4);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0xaa666666); // 灰色
            DrawUtil.drawCircle(canvas, mX, mY, mPaintSize / 2 + mPaintSize / 8, mPaint);

            mPaint.setStrokeWidth(mPaintSize / 16);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0xaaffffff); // 白色
            DrawUtil.drawCircle(canvas, mX, mY, mPaintSize / 2 + mPaintSize / 32, mPaint);

            mPaint.setStyle(Paint.Style.FILL);
            if (!isCopying) {
                mPaint.setColor(0x44ff0000); // 红色
                DrawUtil.drawCircle(canvas, mX, mY, mPaintSize / 2, mPaint);
            } else {
                mPaint.setColor(0x44000088); // 蓝色
                DrawUtil.drawCircle(canvas, mX, mY, mPaintSize / 2, mPaint);
            }
        }

        /**
         * 判断是否点中
         */
        public boolean isInIt(float x, float y) {
            if ((mX - x) * (mX - x) + (mY - y) * (mY - y) <= mPaintSize * mPaintSize) {
                return true;
            }
            return false;
        }

    }
    
//    pr


    // ===================== api ==============

    public void save() {
        try {
            initCanvas();
            draw(mBitmapCanvas, pathStackBackup, false);
            draw(mBitmapCanvas, mPathStack, false);
            mGraffitiListener.onSaved(mGraffitiBitmap);
            // 释放图片
           /* mGraffitiBitmap.recycle();
            mBitmap.recycle();*/
        } catch (Throwable e) {//异常 �? error
            e.printStackTrace();
            mGraffitiListener.onError(ERROR_SAVE, "save error");
            return;
        }
        mGraffitiListener.onSaved(mGraffitiBitmap);
    }

    /**
     * 清屏
     */
    public void clear() {
        mPathStack.clear();
        pathStackBackup.clear();
        initCanvas();
        invalidate();
    }

    /**
     * 撤销
     */
    public void undo() {
        if (mPathStack.size() > 0) {
            mPathStack.remove(mPathStack.size() - 1);
            initCanvas();
            draw(mBitmapCanvas, pathStackBackup, false);
            draw(mBitmapCanvas, mPathStack, false);
            invalidate();
        } else if (pathStackBackup.size() > 0) {
            pathStackBackup.remove(pathStackBackup.size() - 1);
            initCanvas();
            draw(mBitmapCanvas, pathStackBackup, false);
            invalidate();
        }
    }

    /**
     * 是否有修改
     */
    public boolean isModified() {
        return mPathStack.size() != 0 || pathStackBackup.size() != 0;
    }

    /**
     * 居中图片
     */
    public void centrePic() {
        if (mScale > 1) {
            new Thread(new Runnable() {
                boolean isScaling = true;

                public void run() {
                    do {
                        mScale -= 0.2f;
                        if (mScale <= 1) {
                            mScale = 1;
                            isScaling = false;
                        }
                        judgePosition();
                        postInvalidate();
                        try {
                            Thread.sleep(TIME_SPAN / 2);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } while (isScaling);

                }
            }).start();
        } else if (mScale < 1) {
            new Thread(new Runnable() {
                boolean isScaling = true;

                public void run() {
                    do {
                        mScale += 0.2f;
                        if (mScale >= 1) {
                            mScale = 1;
                            isScaling = false;
                        }
                        judgePosition();
                        postInvalidate();
                        try {
                            Thread.sleep(TIME_SPAN / 2);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } while (isScaling);
                }
            }).start();
        }
    }

    /**
     * 只绘制原图
     *
     * @param justDrawOriginal
     */
    public void setJustDrawOriginal(boolean justDrawOriginal) {
        isJustDrawOriginal = justDrawOriginal;
        invalidate();
    }

    public boolean isJustDrawOriginal() {
        return isJustDrawOriginal;
    }

    public void setColor(int color) {
        this.mColor = color;
        invalidate();
    }

    public int getColor() {
        return mColor;
    }

    public void setScale(float scale) {
        this.mScale = scale;
        judgePosition();
        invalidate();
    }

    public float getScale() {
        return mScale;
    }

    public void setPen(Pen pen) {
        if (pen == null) {
            throw new RuntimeException("Pen can't be null");
        }
        mPen = pen;
        resetMatrix();
        invalidate();
    }

    public Pen getPen() {
        return mPen;
    }

    public void setShape(Shape shape) {
        if (shape == null) {
            throw new RuntimeException("Shape can't be null");
        }
        mShape = shape;
        invalidate();
    }

    public Shape getShape() {
        return mShape;
    }

    public void setTransX(float transX) {
        this.mTransX = transX;
        judgePosition();
        invalidate();
    }

    public float getTransX() {
        return mTransX;
    }

    public void setTransY(float transY) {
        this.mTransY = transY;
        judgePosition();
        invalidate();
    }

    public float getTransY() {
        return mTransY;
    }


    public void setPaintSize(float paintSize) {
        mPaintSize = paintSize;
    }

    public float getPaintSize() {
        return mPaintSize;
    }
}
