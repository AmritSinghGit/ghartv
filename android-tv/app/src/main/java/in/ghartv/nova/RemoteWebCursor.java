package in.ghartv.nova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

/** On-demand D-pad pointer over this activity's provider view, not system input injection.
 *  No accessibility service, DOM script, automation flag, provider rewrite or polling.
 */
public final class RemoteWebCursor extends View {
    private final RemoteCursorState state=new RemoteCursorState();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private View target;
    private boolean enabled=true,scrollMode,scheduled,pressed;
    private long lastFrame,started,downAt;
    private int clickCode=-1;
    private float clickX,clickY;
    private final Runnable frame=()->tick();

    public RemoteWebCursor(Context context){
        super(context);density=getResources().getDisplayMetrics().density;
        setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(INVISIBLE);
    }
    public void target(View view){cancel();target=view;}
    public void enable(boolean value){cancel();enabled=value;setVisibility(value?VISIBLE:INVISIBLE);invalidate();}
    public boolean enabled(){return enabled;}
    public void scrollMode(boolean value){cancel();scrollMode=value;invalidate();}
    public boolean scrolling(){return scrollMode;}
    public void enter(){if(enabled&&target!=null){setVisibility(VISIBLE);bringToFront();invalidate();}}
    public void leave(){cancel();setVisibility(INVISIBLE);}
    public void cancel(){
        state.stop();removeCallbacks(frame);scheduled=false;
        if(pressed)touch(MotionEvent.ACTION_CANCEL,SystemClock.uptimeMillis());
        pressed=false;clickCode=-1;invalidate();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){state.resize(w,h,12*density);}
    private int direction(int key){
        switch(key){case KeyEvent.KEYCODE_DPAD_LEFT:return RemoteCursorState.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:return RemoteCursorState.RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP:return RemoteCursorState.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:return RemoteCursorState.DOWN;default:return 0;}
    }
    public boolean handle(KeyEvent event){
        if(!enabled||target==null)return false;
        final int key=event.getKeyCode(),dir=direction(key);
        if(dir!=0){
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                enter();state.setDirection(dir,true);
                if(!scheduled){started=lastFrame=SystemClock.uptimeMillis();schedule();}
            }else if(event.getAction()==KeyEvent.ACTION_UP){state.setDirection(dir,false);if(!state.moving()){removeCallbacks(frame);scheduled=false;}}
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER){
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && !pressed){
                enter();state.stop();removeCallbacks(frame);scheduled=false;
                clickCode=key;clickX=state.x();clickY=state.y();pressed=true;downAt=SystemClock.uptimeMillis();
                touch(MotionEvent.ACTION_DOWN,downAt);invalidate();
            }else if(event.getAction()==KeyEvent.ACTION_UP && pressed && key==clickCode){
                touch(event.isCanceled()?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,SystemClock.uptimeMillis());pressed=false;clickCode=-1;invalidate();
            }
            return true;
        }
        if(key==KeyEvent.KEYCODE_PAGE_UP || key==KeyEvent.KEYCODE_PAGE_DOWN){
            if(event.getAction()==KeyEvent.ACTION_DOWN)scroll(0,key==KeyEvent.KEYCODE_PAGE_UP?-6f:6f);
            return true;
        }
        return false;
    }
    private void schedule(){if(!scheduled){scheduled=true;postOnAnimation(frame);}}
    private void tick(){
        scheduled=false;
        if(!enabled||target==null||!state.moving()||!isShown())return;
        long now=SystemClock.uptimeMillis();float dt=Math.min(.05f,Math.max(.001f,(now-lastFrame)/1000f));lastFrame=now;
        float acceleration=Math.min(1.9f,1f+(now-started)/1800f);
        if(scrollMode){scroll(state.horizontal()*dt*12,state.vertical()*dt*12);}
        else{
            state.advance(dt,density*360*acceleration);
            hover();if(state.edgeInDirection())scroll(state.horizontal()*dt*7,state.vertical()*dt*7);
        }
        invalidate();schedule();
    }
    private void hover(){
        MotionEvent event=mouse(MotionEvent.ACTION_HOVER_MOVE,0,0);
        try{target.dispatchGenericMotionEvent(event);}finally{event.recycle();}
    }
    private void scroll(float dx,float dy){
        MotionEvent event=mouse(MotionEvent.ACTION_SCROLL,dx,-dy);
        boolean handled;
        try{handled=target.dispatchGenericMotionEvent(event);}finally{event.recycle();}
        if(!handled && target instanceof WebView)target.scrollBy(Math.round(dx*48*density),Math.round(dy*48*density));
    }
    private MotionEvent mouse(int action,float horizontal,float vertical){
        MotionEvent.PointerProperties properties=new MotionEvent.PointerProperties();properties.id=0;properties.toolType=MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=state.x();coords.y=state.y();coords.pressure=0;coords.size=1;
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL,horizontal);coords.setAxisValue(MotionEvent.AXIS_VSCROLL,vertical);
        long now=SystemClock.uptimeMillis();
        return MotionEvent.obtain(now,now,action,1,new MotionEvent.PointerProperties[]{properties},new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,InputDevice.SOURCE_MOUSE,0);
    }
    private void touch(int action,long when){
        if(target==null)return;
        MotionEvent event=MotionEvent.obtain(downAt,when,action,clickX,clickY,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{target.dispatchTouchEvent(event);}finally{event.recycle();}
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float radius=(pressed?8:10)*density;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5*density);paint.setColor(Color.BLACK);
        canvas.drawCircle(state.x(),state.y(),radius,paint);
        paint.setStrokeWidth(2.5f*density);paint.setColor(scrollMode?Color.WHITE:0xFF8CF4D5);
        canvas.drawCircle(state.x(),state.y(),radius,paint);
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(state.x(),state.y(),2*density,paint);
    }
    @Override protected void onDetachedFromWindow(){cancel();target=null;super.onDetachedFromWindow();}
}
