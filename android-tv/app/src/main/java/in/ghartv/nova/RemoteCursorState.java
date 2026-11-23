package in.ghartv.nova;

/** Geometry only: no Android, provider, device identifiers, network or timers. */
public final class RemoteCursorState {
    public static final int LEFT=1, RIGHT=2, UP=4, DOWN=8;
    private float x, y, width, height, inset;
    private boolean placed;
    private int directions;

    public void resize(float w, float h, float margin) {
        if (!Float.isFinite(w) || !Float.isFinite(h) || w < 0 || h < 0) return;
        float oldW=width, oldH=height;
        width=w; height=h; inset=Math.max(0,Math.min(margin,Math.min(w,h)/2));
        if(!placed && w>0 && h>0){x=w/2; y=h/2; placed=true;}
        else if(oldW>0 && oldH>0){x=x*w/oldW; y=y*h/oldH;}
        clamp();
    }
    public void setDirection(int direction, boolean down) {
        if(direction!=LEFT && direction!=RIGHT && direction!=UP && direction!=DOWN)return;
        if(down)directions|=direction; else directions&=~direction;
    }
    public void stop(){directions=0;}
    public boolean moving(){return directions!=0;}
    public int horizontal(){return ((directions&RIGHT)!=0?1:0)-((directions&LEFT)!=0?1:0);}
    public int vertical(){return ((directions&DOWN)!=0?1:0)-((directions&UP)!=0?1:0);}
    public float x(){return x;}
    public float y(){return y;}
    public void position(float px,float py){if(Float.isFinite(px)&&Float.isFinite(py)){x=px;y=py;clamp();}}
    public void advance(float seconds, float speed){
        if(!Float.isFinite(seconds)||!Float.isFinite(speed)||seconds<=0||speed<=0)return;
        float dt=Math.min(seconds,.05f);int dx=horizontal(),dy=vertical();
        float diagonal=dx!=0&&dy!=0?.70710678f:1f;
        x+=dx*speed*dt*diagonal;y+=dy*speed*dt*diagonal;clamp();
    }
    public boolean edgeInDirection(){return horizontal()<0&&x<=inset+.5f || horizontal()>0&&x>=width-inset-.5f || vertical()<0&&y<=inset+.5f || vertical()>0&&y>=height-inset-.5f;}
    private void clamp(){x=Math.max(inset,Math.min(Math.max(inset,width-inset),x));y=Math.max(inset,Math.min(Math.max(inset,height-inset),y));}
}
