package net.complynx.lightcontroller;

import android.content.Context;
import android.content.res.TypedArray;
import android.drm.DrmStore;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.RelativeLayout;

import java.util.Timer;
import java.util.TimerTask;

public class NippleKnob extends RelativeLayout {
    public final static String TAG = "NippleKnob";

    private float[] hsv;
    private final static float baseElevation = 6;
    private final static float hue_speed = 0.05f, saturation_speed = 0.0005f;
    protected float passiveAngle = 90f;
    protected Timer timer = null;
    private Handler handler;
    private final static int rate = 1000/25; // 25 frames/sec;

    interface KnobListener {
        public void onColorChanged(float[]hsv);
    }
    private KnobListener m_listener;

    public void setListener(KnobListener l) {
        m_listener = l;
    }

    private float cx, cy, radius;

    public NippleKnob(Context context) {
        super(context);
        init(null, 0);
    }
    public NippleKnob(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }
    public NippleKnob(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }


    private float cartesianToPolar(float x, float y) {
        return (float) -Math.toDegrees(Math.atan2(x - 0.5f, y - 0.5f));
    }
    private float getCentreDistance(float x, float y){
        double dx = x - cx, dy = y - cy;
        return (float) Math.sqrt(dx*dx + dy*dy);
    }
    private boolean isInRadius(float x, float y){
        return getCentreDistance(x, y) < radius;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                return onDown(event);
            case MotionEvent.ACTION_POINTER_DOWN:
                return false;
            case MotionEvent.ACTION_MOVE:
                return onMove(event);
            case MotionEvent.ACTION_UP:
                return onUp(event);
            case MotionEvent.ACTION_POINTER_UP:
                if(event.getActionIndex() != 0) return false;
                return onUp(event);
            default:
                return false;
        }
    }

    private boolean is_touching = false;
    private boolean rim_touch = false;
    private float startX, startY, dx, dy;
    private boolean onUp(MotionEvent event){
        if(!is_touching) return false;
        float x = event.getX(), y = event.getY();
        is_touching = false;
        if(rim_touch){
            return onRim(x, y);
        }else {
            LayoutParams lp = (LayoutParams) nipple.getLayoutParams();
            float nw = nipple.getWidth() / 2;
            lp.leftMargin = (int) (cx - nw);
            lp.topMargin = (int) (cy - nw);
            nipple.setLayoutParams(lp);
        }
        return true;
    }
    private boolean onMove(MotionEvent event){
        if(!is_touching) return false;
        float x = event.getX(), y = event.getY();
        if(rim_touch){
            return onRim(x, y);
        }
        return delayChange(x, y);
    }
    private boolean onDown(MotionEvent event){
        is_touching = true;
        float x = event.getX(), y = event.getY();
        if(isInRadius(x,y)){
            rim_touch = false;
            startX = x; startY = y;

            timer.schedule(new MoverTask(), rate);
            return true;
        }else{
            rim_touch = true;
            return onRim(x, y);
        }
    }

    private class MoverTask extends TimerTask{
        @Override
        public void run() {
            if(is_touching && !rim_touch)
                handler.sendEmptyMessage(0);
        }
    }

    private boolean delayChange(float x, float y){
        dx = x - startX;
        dy = y - startY;
        return true;
    }
    private boolean onRim(float x, float y){
        x /= getWidth();
        y /= getHeight();
        float polar = cartesianToPolar(1 - x,1 - y);
        if(Float.isNaN(polar)) return false;

        float angleLimit = 180 - passiveAngle/2;

        if(polar > angleLimit){
            polar = angleLimit;
        }else if(polar < -angleLimit){
            polar = -angleLimit;
        }

        float val = polar + angleLimit;
        val /= 360 - passiveAngle;
        Log.d(TAG, "onRim: "+ val);

        hsv[2] = val;
        setColor(hsv);

        return true;
    }

    static class Nipple extends View {
        private float radius = 20;
        private float cx, cy;
//        private float dx = 5;

        Paint rim, measure, shadow;

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh){
            cx = (float) ((float) w / 2.);
            cy = (float) ((float) h / 2.);
            radius = Math.min(cx, cy);
//            rim.setTextSize(3.5f * radius);
            float sw = radius/3;
            rim.setStrokeWidth(sw);
            radius -= sw/2;
//            measure.setTextSize(3.5f * radius);
//            Log.d(TAG, "onSizeChanged: " + cx + " " + cy + " " + radius);

            invalidate();
            super.onSizeChanged(w,h,oldw,oldh);
        }

        public int getColor(){
            return measure.getColor();
        }
        public void setColor(int color){
            float[] hsv_r = new float[3];
            Color.colorToHSV(color, hsv_r);
            setColor(hsv_r);
        }
        public void setColor(float[]hsv){
            float[] hsv_r = hsv.clone();
            hsv_r[2] = 1;
            measure.setColor(Color.HSVToColor(hsv_r));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas){
//            canvas.drawText("\u25cf", 0, 2*cy, measure);
//            canvas.drawText("\u23cb", 0, 2*cy, rim);
            canvas.drawCircle(cx, cy, radius, measure);
            canvas.drawCircle(cx, cy, radius, rim);
        }

        Nipple(Context context){
            super(context);

            rim = new Paint();
            rim.setAntiAlias(true);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(5);
            rim.setColor(Color.parseColor("#cccccc"));
            rim.setShadowLayer(2, 1,1, Color.BLACK);

            measure = new Paint();
            measure.setAntiAlias(true);
            measure.setStyle(Paint.Style.FILL);
            setColor(Color.GRAY);

            invalidate();
        }
    }
    static class Colors extends View{
        private static final float sin60 = 0.866025403784438646763723f;
        private float radius = 20;
        private float gap;
        private float cx, cy;
        private int painter_color;
        private int[] left_colors;
        private int[] right_colors;
        private int[] down_colors;
        private static final int[] alphas = {255, 255,  250,  80,   0};
        private static final float[] dh =   {70,  60,   50,   30,   0};
        private static float[] positions =  {0f,  0.1f, 0.2f, 0.4f, 0.8f};
        private float[] hsv;
        private Paint painter;
        private Shader leftShader, rightShader, saturationShader;

        private float hueCircle(float hue){
            while(hue>=360) hue -= 360;
            while(hue<0) hue += 360;
            return hue;
        }

        public void setGap(float g){
            radius += gap;
            gap = g;
            radius -= gap;
            resetShaders();
            invalidate();
        }
        public float getGap(){ return gap; }

        Colors(Context context){
            super(context);
            hsv = new float[3];
            left_colors = new int[positions.length];
            right_colors = new int[positions.length];
            down_colors = new int[positions.length];
            down_colors[positions.length - 1] = Color.TRANSPARENT;
            left_colors[positions.length - 1] = Color.TRANSPARENT;
            right_colors[positions.length - 1] = Color.TRANSPARENT;
            down_colors[3] = Color.argb(80, 255,255,255);
            down_colors[2] = Color.argb(130, 255,255,255);
            down_colors[1] = Color.argb(200, 255,255,255);
            down_colors[0] = Color.WHITE;

            setOutlineProvider(outlineProvider);
            setGap(20);

            painter = new Paint();
            painter.setAntiAlias(true);
            painter.setStyle(Paint.Style.FILL);
        }
        @Override
        protected void onDraw(Canvas canvas){
//            canvas.drawCircle(cx+dx, cy+dx, radius, shadow);
            painter.setShader(null);
            painter.setColor(painter_color);
            canvas.drawCircle(cx, cy, radius, painter);
            painter.setShader(leftShader);
            canvas.drawCircle(cx, cy, radius, painter);
            painter.setShader(rightShader);
            canvas.drawCircle(cx, cy, radius, painter);
            painter.setShader(saturationShader);
            canvas.drawCircle(cx, cy, radius, painter);
        }

        private void resetShaders(){
            float rs = radius*sin60;
            float r2 = radius/2f;

            leftShader = new LinearGradient(
                    cx - rs, cy - r2, cx + rs, cy + r2,
                    left_colors,
                    positions,
                    Shader.TileMode.REPEAT
            );

            rightShader = new LinearGradient(
                    cx + rs, cy - r2, cx - rs, cy + r2,
                    right_colors,
                    positions,
                    Shader.TileMode.REPEAT
            );

            saturationShader = new LinearGradient(
                    cx, cy + radius, cx, cy - radius,
                    down_colors,
                    positions,
                    Shader.TileMode.REPEAT
            );
        }

        ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(
                        (int)(cx - radius),
                        (int)(cy - radius),
                        (int)(cx + radius),
                        (int)(cy + radius)
                );
            }
        };

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh){
            cx = (float) ((float) w / 2.);
            cy = (float) ((float) h / 2.);
            radius = Math.min(cx, cy) - gap;

            resetShaders();
            invalidate();
        }

        public int getColor(){
            return Color.HSVToColor(hsv);
        }
        public void setColor(float [] _hsv){
            float[] hsv_r = _hsv.clone();
            hsv_r[1] = 1;
            hsv_r[2] = 1;

            painter_color = Color.HSVToColor(hsv_r);

            hsv = _hsv.clone();

            for(int i=0; i< positions.length; ++i){
                hsv_r[0] = hueCircle(hsv[0] - dh[i]);
                left_colors[i] = Color.HSVToColor(alphas[i], hsv_r);
                hsv_r[0] = hueCircle(hsv[0] + dh[i]);
                right_colors[i] = Color.HSVToColor(alphas[i], hsv_r);
            }

            resetShaders();
            invalidate();
        }
    }
    static class Knob extends View{
        private float gap=0;
        private Paint painter;
        private Paint tick_painter;
        private Paint bg_painter;
        private float startAngle;
        private float passiveAngle;
        private float endAngle;
        private float mLeft = 0;
        private float mRight = 0;
        private float mTop = 0;
        private float mBottom = 0;
        private float value = 0;

        public float getGap(){
            return gap;
        }
        public void setGap(float gap){
            this.gap = gap;

            painter.setStrokeWidth(gap/2);
            bg_painter.setStrokeWidth(gap);
            tick_painter.setStrokeWidth(gap/5);

            setSize(getWidth(), getHeight());
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh){
            setSize(w,h);

            invalidate();
        }

        private void setSize(int w, int h){
            float size, top_pad=0, left_pad=0, stroke_offset = gap/2;
            if(w>h){
                size = h;
                left_pad = (w-h)/2;
            }else{
                size = w;
                top_pad = (h-w)/2;
            }
            left_pad += stroke_offset;
            top_pad += stroke_offset;
            size -= stroke_offset * 2;

            mLeft = left_pad;
            mTop = top_pad;
            mRight = left_pad + size;
            mBottom = top_pad + size;

            setGradient();
        }

        private void setColor(float[] hsv){
            float[] _hsv = hsv.clone();
            value = hsv[2];
            _hsv[2] = 1;
            _hsv[1] = 0.3f;
            bg_painter.setColor(Color.HSVToColor(127, _hsv));
            invalidate();
        }

        Knob(Context context){
            super(context);

            painter = new Paint();
            painter.setAntiAlias(true);
            painter.setStyle(Paint.Style.STROKE);
            painter.setStrokeWidth(10);
            painter.setStrokeCap(Paint.Cap.ROUND);

            bg_painter = new Paint();
            bg_painter.setAntiAlias(true);
            bg_painter.setStyle(Paint.Style.STROKE);
            painter.setStrokeWidth(10);

            tick_painter = new Paint();
            tick_painter.setAntiAlias(true);
            tick_painter.setStyle(Paint.Style.STROKE);
            tick_painter.setStrokeCap(Paint.Cap.ROUND);
            tick_painter.setStrokeWidth(5);
            tick_painter.setColor(0xff000000);

            setSize(1,1);
            setArc(90f);
        }

        private void setGradient(){
            float cx = getWidth()/2;
            float cy= getHeight()/2;
            float pa = passiveAngle/720f;
            SweepGradient gradient = new SweepGradient(cx, cy,
                    new int[] {Color.BLACK, Color.WHITE},
                    new float[] {pa, 1f - pa}
            );
            Matrix gradientMatrix = new Matrix();
            gradientMatrix.preRotate(90f, cx, cy);
            gradient.setLocalMatrix(gradientMatrix);
            painter.setShader(gradient);
        }

        public void setArc(float passiveAngle){
            this.passiveAngle = passiveAngle;
            startAngle = 90F + (passiveAngle/2);
            endAngle = 360F - passiveAngle;
            setGradient();
        }

        @Override
        protected void onDraw(Canvas canvas){
            canvas.drawOval(mLeft, mTop, mRight, mBottom, bg_painter);
            canvas.drawArc(mLeft, mTop, mRight, mBottom, startAngle, endAngle,
                    false, painter);

            float a = (value - 0.5f) * (360f - passiveAngle),cx = getWidth()/2f;
            canvas.save();
            canvas.rotate(a, cx, getHeight()/2f);
            canvas.drawLine(cx, mTop, cx, mTop+gap, tick_painter);
            canvas.restore();
        }
    }

    public float getGap(){
        return colors.getGap();
    }
    public void setGap(float gap){
        colors.setGap(gap);
        knob.setGap(gap);
    }

    Nipple nipple;
    Colors colors;
    Knob knob;

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh){
        int c = Math.min(w,h)/3;
        setGap(c/3);

        cx = w/2;
        cy = h/2;
        radius = Math.min(cx, cy) - c/2;
        LayoutParams lp = new LayoutParams(c,c);
        lp.leftMargin = (int) (cx - c/2);
        lp.topMargin = (int) (cy - c/2);
        nipple.setLayoutParams(lp);

        invalidate();
    }

    public void setColor(int color){
        float[] _hsv = new float[3];
        Color.colorToHSV(color, _hsv);
        if(_hsv[2] == 0 || _hsv[1] == 0){
            _hsv[0] = hsv[0];
        }
        setColor(_hsv);
    }
    public void setColor(float[] _hsv) {
        if(_hsv.length < 3) return;
        System.arraycopy(_hsv, 0, hsv, 0, 3);

        nipple.setColor(hsv);
        colors.setColor(hsv);
        knob.setColor(hsv);

        if(m_listener != null){
            m_listener.onColorChanged(hsv);
        }
    }
    public int getColor() {
        return Color.HSVToColor(hsv);
    }

    void step(){
        if(is_touching && !rim_touch) {
            hsv[0] += dx * hue_speed;
            while (hsv[0] >= 360) hsv[0] -= 360;
            while (hsv[0] < 0) hsv[0] += 360;
            hsv[1] += dy * saturation_speed;
            if (hsv[1] > 1) hsv[1] = 1;
            if (hsv[1] < 0) hsv[1] = 0;
            float dist = (float)Math.sqrt((double) dx*dx + (double) dy*dy);
            float cdx = dx, cdy = dy;
            if(dist > radius) {
                cdx *= radius/dist;
                cdy *= radius/dist;
            }

            LayoutParams lp = (LayoutParams) nipple.getLayoutParams();
            float nw = nipple.getWidth()/2;
            lp.leftMargin = (int) (cx - nw + cdx);
            lp.topMargin = (int) (cy - nw + cdy);
            nipple.setLayoutParams(lp);

            setColor(hsv);
            timer.schedule(new MoverTask(), rate);
        }
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.NippleKnob, defStyle, 0);

        hsv = new float[3];

        colors = new Colors(getContext());
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        colors.setLayoutParams(lp);
        addView(colors);
        colors.setElevation(baseElevation);

        nipple = new Nipple(getContext());
        lp = new LayoutParams(100, 100);
        lp.addRule(CENTER_IN_PARENT);
        nipple.setLayoutParams(lp);
        addView(nipple);
        nipple.setElevation(baseElevation + 1);

        knob = new Knob(getContext());
        lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        knob.setLayoutParams(lp);
        knob.setArc(passiveAngle);
        addView(knob);
        knob.setElevation(baseElevation);

        timer = new Timer();
        handler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg){
                step();
                handler.removeMessages(0);
            }
        };

        setColor(Color.GREEN);
        setGap(50);

        a.recycle();
    }
}


//class TryActivity extends AppCompatActivity{
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.sample_nipple_knob);
//    }
//}
