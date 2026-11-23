import in.ghartv.nova.RemoteCursorState;
public final class RemoteCursorSmoke {
    private static int checks;
    private static void ok(boolean value){checks++;if(!value)throw new AssertionError("Cursor check "+checks);}
    public static void main(String[] args){
        RemoteCursorState s=new RemoteCursorState();s.resize(1000,600,10);ok(s.x()==500&&s.y()==300);
        s.setDirection(RemoteCursorState.RIGHT,true);s.advance(.05f,200);ok(s.x()==510&&s.y()==300);
        s.setDirection(RemoteCursorState.LEFT,true);s.advance(.05f,200);ok(s.x()==510);
        s.setDirection(RemoteCursorState.LEFT,false);s.setDirection(RemoteCursorState.DOWN,true);s.advance(.05f,200);ok(Math.abs(s.x()-517.071f)<.01);
        s.stop();ok(!s.moving());float x=s.x();s.advance(.05f,200);ok(s.x()==x);
        s.position(9999,-50);ok(s.x()==990&&s.y()==10);
        s.setDirection(RemoteCursorState.RIGHT,true);ok(s.edgeInDirection());
        s.position(400,300);s.advance(9,200);ok(s.x()==410);
        s.resize(500,300,10);ok(s.x()==205&&s.y()==150);
        s.position(Float.NaN,0);ok(s.x()==205);
        s.advance(Float.NaN,200);ok(s.x()==205);
        s.resize(0,0,10);ok(s.x()==0&&s.y()==0);
        s.resize(10,10,20);ok(s.x()==5&&s.y()==5);
        s.stop();s.setDirection(999,true);ok(!s.moving());
        System.out.println("REMOTE_CURSOR_GEOMETRY="+checks+"_PASS");
    }
}
