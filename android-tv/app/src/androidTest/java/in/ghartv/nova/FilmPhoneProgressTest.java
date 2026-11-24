package in.ghartv.nova;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class FilmPhoneProgressTest {
    @Test public void repeatedClockProgressDoesNotCrossSourceEpochs(){
        FilmPhoneProgress p=new FilmPhoneProgress();
        assertFalse(p.sample(1,1000,1000,true));
        assertFalse(p.sample(1,1300,1500,true));
        assertTrue(p.sample(1,2100,2250,true));
        assertFalse(p.sample(2,2600,2500,true));
        assertFalse(p.sample(2,3200,2500,true));
        assertTrue(p.sample(2,3900,3300,true));
        assertFalse(p.sample(3,4100,12000,false));
        assertFalse(p.sample(3,4900,12000,true));
        assertTrue(p.sample(3,5800,12600,true));
        assertFalse(p.sample(4,6200,-1,true));
    }
}
