package studio.filmspace.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.ar.core.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    GLSurfaceView surface;
    StudioRenderer renderer;
    Session session;
    boolean resumed, wantCamera, installRequested, recording, recordBusy;
    Button mode, record, lens;
    TextView status;
    Uri lastVideo;
    int accent = Color.rgb(212,245,122);
    String currentStatus = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setNavigationBarColor(Color.rgb(14,21,29));
        getWindow().setStatusBarColor(Color.rgb(14,21,29));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(14,21,29));
        root.setPadding(dp(12),dp(4),dp(12),dp(4));
        root.setOnApplyWindowInsetsListener((v,insets)->{ v.setPadding(Math.max(dp(12),insets.getSystemWindowInsetLeft()), Math.max(dp(4),insets.getSystemWindowInsetTop()),Math.max(dp(12),insets.getSystemWindowInsetRight()),Math.max(dp(4),insets.getSystemWindowInsetBottom())); return insets; });
        setContentView(root);
        LinearLayout header = row();
        TextView title = new TextView(this); title.setText("FILM SPACE  /  ANDROID"); title.setTextSize(15); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setTextColor(accent); header.addView(title);
        status = new TextView(this); status.setTextSize(12); status.setGravity(Gravity.CENTER); status.setTextColor(Color.LTGRAY); header.addView(status,new LinearLayout.LayoutParams(0,dp(38),1));
        add(header,"?",this::help);
        root.addView(header);
        surface = new GLSurfaceView(this); surface.setEGLContextClientVersion(2);
        surface.setEGLConfigChooser((egl,display)->{
            int[] attrs={0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3025,16,0x3040,4,0x3142,1,0x3038};
            javax.microedition.khronos.egl.EGLConfig[] configs=new javax.microedition.khronos.egl.EGLConfig[1]; int[] count=new int[1];
            if(!egl.eglChooseConfig(display,attrs,configs,1,count)||count[0]==0)throw new RuntimeException("No recording-compatible OpenGL configuration");
            return configs[0];
        });
        renderer = new StudioRenderer(this);
        surface.setRenderer(renderer); surface.setPreserveEGLContextOnPause(true);
        root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        touchControls();
        LinearLayout toolbar = row();
        add(toolbar,"+ Actor",()->queue(()->renderer.addActor()));
        add(toolbar,"Delete",()->queue(()->renderer.deleteActor()));
        hold(toolbar,"↶",()->renderer.rotateActor(-3)); hold(toolbar,"↷",()->renderer.rotateActor(3));
        mode=add(toolbar,"Camera",this::toggleMode);
        lens=add(toolbar,"35 mm",()->queue(()->{renderer.lensIndex=(renderer.lensIndex+1)%4;runOnUiThread(()->lens.setText(renderer.lens()+" mm"));}));
        record=add(toolbar,"● Record",this::toggleRecording); record.setTextColor(accent);
        root.addView(scroll(toolbar));
        LinearLayout lower=row();
        hold(lower,"←",()->renderer.move(-.035f,0)); hold(lower,"↑",()->renderer.move(0,-.035f)); hold(lower,"↓",()->renderer.move(0,.035f)); hold(lower,"→",()->renderer.move(.035f,0));
        hold(lower,"Height +",()->renderer.height(.025f)); hold(lower,"Height −",()->renderer.height(-.025f));
        add(lower,"Lock",()->queue(()->renderer.lock())); add(lower,"Return",()->queue(()->renderer.restore()));
        add(lower,"Center",()->queue(()->renderer.center())); add(lower,"Video",this::openVideo);
        root.addView(scroll(lower));
        setStatus("EDIT · Drag to orbit / tap an actor");
        if(!getPreferences(0).getBoolean("intro",false)) { help(); getPreferences(0).edit().putBoolean("intro",true).apply(); }
    }
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    HorizontalScrollView scroll(View child){HorizontalScrollView s=new HorizontalScrollView(this);s.setHorizontalScrollBarEnabled(false);s.addView(child);return s;}
    Button add(LinearLayout parent,String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),0,dp(12),0);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(32,43,55));bg.setCornerRadius(dp(10));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44));p.setMargins(dp(3),dp(3),dp(3),dp(3));parent.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    void hold(LinearLayout p,String text,Runnable action){Button b=add(p,text,()->{}); Handler h=new Handler(getMainLooper()); Runnable tick=new Runnable(){public void run(){if(!resumed)return;queue(action);h.postDelayed(this,35);}};b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){h.post(tick);return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){h.removeCallbacks(tick);return true;}return true;});}
    void queue(Runnable r){surface.queueEvent(r);}
    void touchControls(){
        ScaleGestureDetector scale=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float f=d.getScaleFactor();queue(()->renderer.zoom(f));return true;}});
        surface.setOnTouchListener(new View.OnTouchListener(){float x,y,startX,startY;boolean multi;
            public boolean onTouch(View v,MotionEvent e){scale.onTouchEvent(e);float nx=e.getX(),ny=e.getY();
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=startX=nx;y=startY=ny;multi=false;}
                if(e.getPointerCount()>1)multi=true;
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!multi){float dx=nx-x,dy=ny-y;queue(()->renderer.drag(dx,dy));}
                if(e.getActionMasked()==MotionEvent.ACTION_UP&&!multi&&Math.hypot(nx-startX,ny-startY)<dp(9))queue(()->renderer.pick(nx,ny));
                x=nx;y=ny;return true;}
        });
    }
    void setStatus(String s){if(s.equals(currentStatus))return;currentStatus=s;runOnUiThread(()->status.setText(s));}
    void message(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    void help(){new AlertDialog.Builder(this).setTitle("Film Space · راهنما").setMessage(
        "نسخهٔ آزمایشی مستقل برای اندروید\n\n"+
        "EDIT: با کشیدن فضای خالی دور صحنه بچرخید؛ با دو انگشت زوم کنید. آدمک را لمس و سپس جابه‌جا کنید. لمس فضای خالی انتخاب را لغو می‌کند.\n\n"+
        "Camera: گوشی را افقی نگه دارید و برای دنبال‌کردن حرکت کمی در محیط روشن حرکت دهید. Google Play Services for AR باید نصب باشد. Center نقطهٔ شروع را تنظیم می‌کند.\n\n"+
        "Record: فقط صحنهٔ سه‌بعدی با کیفیت 720p ضبط می‌شود؛ دکمه‌ها در ویدیو نیستند. اجازهٔ میکروفون اختیاری است. فیلم در Movies/FilmSpace ذخیره می‌شود. هنگام خروج ضبط متوقف می‌شود.\n\n"+
        "چیدمان خودکار روی گوشی ذخیره می‌شود. پردازش صحنه محلی است.\n\n"+
        "قابلیت AR توسط Google Play Services for AR ارائه می‌شود و تابع حریم خصوصی Google است: policies.google.com/privacy\n\n"+
        "Inspired by maxprokopp/film-space. Independent Android implementation; not an official release."
        ).setPositiveButton("باشه",null).show();}
    void toggleMode(){if(recording||recordBusy){message("اول ضبط را متوقف کن");return;}wantCamera=!wantCamera;if(wantCamera)startAR();else stopAR();}
    void startAR(){
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},10);return;}
        try {
            if(ArCoreApk.getInstance().requestInstall(this,!installRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED){installRequested=true;return;}
            if(session==null){session=new Session(this);Config cfg=new Config(session);cfg.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);cfg.setLightEstimationMode(Config.LightEstimationMode.DISABLED);cfg.setFocusMode(Config.FocusMode.AUTO);session.configure(cfg);}
            session.resume(); final Session s=session;queue(()->{renderer.session=s;renderer.camera=true;renderer.recenter=true;});mode.setText("Edit");
        }catch(Exception e){wantCamera=false;mode.setText("Camera");message("AR شروع نشد. Google Play Services for AR را نصب/به‌روز کن.\n"+e.getClass().getSimpleName());}
    }
    void stopAR(){CountDownLatch latch=new CountDownLatch(1);queue(()->{renderer.session=null;renderer.camera=false;latch.countDown();});try{latch.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}if(session!=null)session.pause();mode.setText("Camera");setStatus("EDIT · Drag to orbit / tap an actor");}
    void toggleRecording(){
        if(recordBusy)return;
        if(recording){stopRecording();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(0).getBoolean("micAsked",false)){
            getPreferences(0).edit().putBoolean("micAsked",true).apply();requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);return;
        }
        recordBusy=true;record.setEnabled(false);boolean audio=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        queue(()->{try{renderer.startRecording(audio);runOnUiThread(()->{recording=true;recordBusy=false;record.setEnabled(true);record.setText("■ Stop");});}catch(Exception e){message("ضبط شروع نشد: "+e.getMessage());runOnUiThread(()->{recordBusy=false;record.setEnabled(true);});}});
    }
    void stopRecording(){recordBusy=true;record.setEnabled(false);queue(()->finishRecording());}
    void finishRecording(){Uri uri=renderer.stopRecording();runOnUiThread(()->{if(uri!=null){lastVideo=uri;message("ویدیو در گالری / FilmSpace ذخیره شد");}recording=false;recordBusy=false;record.setEnabled(true);record.setText("● Record");});}
    void openVideo(){if(lastVideo==null){message("هنوز ویدیویی در این اجرا ضبط نشده");return;}try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(lastVideo,"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(ActivityNotFoundException e){message("ویدیو را در گالری، پوشهٔ FilmSpace باز کن");}}
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] result){super.onRequestPermissionsResult(request,p,result);if(request==10){if(result.length>0&&result[0]==PackageManager.PERMISSION_GRANTED&&wantCamera)startAR();else{wantCamera=false;message("حالت ویرایش بدون دوربین قابل استفاده است");}}if(request==11)toggleRecording();}
    @Override protected void onResume(){super.onResume();resumed=true;if(surface!=null)surface.onResume();if(wantCamera)startAR();}
    @Override protected void onPause(){
        resumed=false;CountDownLatch done=new CountDownLatch(1);queue(()->{if(renderer.isRecording())finishRecording();renderer.saveScene();renderer.session=null;done.countDown();});
        try{done.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        surface.onPause();if(session!=null)session.pause();super.onPause();
    }
    @Override protected void onDestroy(){if(session!=null)session.close();super.onDestroy();}
}
