package studio.filmspace.android;

import android.content.ContentValues;
import android.media.MediaRecorder;
import android.net.Uri;
import android.opengl.*;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Surface;
import com.google.ar.core.*;
import java.nio.*;
import java.util.*;
import org.json.*;
import javax.microedition.khronos.opengles.GL10;
import static android.opengl.GLES20.*;
import static studio.filmspace.android.Geometry.*;
import studio.filmspace.android.Geometry.Mesh;

public final class StudioRenderer implements GLSurfaceView.Renderer {
    final MainActivity app;
    Session session;
    boolean camera,recenter=true,tracking;
    int width=1,height=1,vx,vy,vw=1,vh=1,lensIndex,selected=-1,program,posLoc,normLoc,mvpLoc,modelLoc,colorLoc,texture;
    float yaw=24,pitch=20,distance=7,targetX=0,targetY=.9f,targetZ=0;
    final float[] view=new float[16],projection=new float[16],vp=new float[16],model=new float[16],mvp=new float[16],arWorld=new float[16],pose=new float[16],cameraWorld=new float[16];
    float[] lockedCamera,lockedEdit;
    final ArrayList<float[]> actors=new ArrayList<>();
    Mesh cube,sphere,floor;
    MediaRecorder recorder;
    Uri videoUri;
    ParcelFileDescriptor videoFd;
    Surface encoderSurface;
    EGLDisplay recordDisplay;
    EGLSurface recordSurface=EGL14.EGL_NO_SURFACE;
    long startNs,lastFrameNs;
    int recordedFrames;
    final float[] white={.81f,.85f,.89f,1},green={.75f,.94f,.40f,1};

    StudioRenderer(MainActivity a){app=a;loadScene();Matrix.setIdentityM(arWorld,0);}
    int lens(){return new int[]{35,50,75,200}[lensIndex];}
    void addActor(){if(actors.size()>=24){app.message("حداکثر ۲۴ آدمک");return;}actors.add(new float[]{targetX,0,targetZ,0});selected=actors.size()-1;}
    void deleteActor(){if(selected>=0&&selected<actors.size()){actors.remove(selected);selected=-1;}}
    void rotateActor(float d){if(selected>=0)actors.get(selected)[3]+=d;}
    void zoom(float f){if(!camera)distance=Math.max(1.5f,Math.min(30,distance/f));}
    void drag(float dx,float dy){if(camera)return;if(selected>=0){float k=distance/Math.max(300,vh);float a=(float)Math.toRadians(yaw);float[] p=actors.get(selected);p[0]+=k*(dx*(float)Math.cos(a)+dy*(float)Math.sin(a));p[2]+=k*(-dx*(float)Math.sin(a)+dy*(float)Math.cos(a));p[0]=clamp(p[0],-18,18);p[2]=clamp(p[2],-18,18);}else{yaw-=dx*.22f;pitch=clamp(pitch+dy*.18f,5,82);}}
    static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
    void move(float right,float forward){if(camera){arWorld[12]+=cameraWorld[0]*right+cameraWorld[8]*forward;arWorld[14]+=cameraWorld[2]*right+cameraWorld[10]*forward;}else{float a=(float)Math.toRadians(yaw);targetX+=right*(float)Math.cos(a)+forward*(float)Math.sin(a);targetZ+=-right*(float)Math.sin(a)+forward*(float)Math.cos(a);}}
    void height(float d){if(camera)arWorld[13]+=d;else targetY=clamp(targetY+d,.1f,8);}
    void center(){if(camera)recenter=true;else{yaw=24;pitch=20;distance=7;targetX=targetZ=0;targetY=.9f;selected=-1;}}
    void lock(){if(camera){if(!tracking){app.message("منتظر شناسایی حرکت بمان");return;}lockedCamera=cameraWorld.clone();}else lockedEdit=new float[]{yaw,pitch,distance,targetX,targetY,targetZ};app.message("زاویه ذخیره شد");}
    void restore(){if(camera&&lockedCamera!=null&&tracking){float[] inverse=new float[16];Matrix.invertM(inverse,0,pose,0);Matrix.multiplyMM(arWorld,0,lockedCamera,0,inverse,0);}else if(!camera&&lockedEdit!=null){yaw=lockedEdit[0];pitch=lockedEdit[1];distance=lockedEdit[2];targetX=lockedEdit[3];targetY=lockedEdit[4];targetZ=lockedEdit[5];}else app.message("اول یک زاویه را Lock کن");}
    void pick(float x,float y){if(camera)return;selected=-1;float best=Float.MAX_VALUE;for(int i=0;i<actors.size();i++){float[] p=actors.get(i),out=new float[4];Matrix.multiplyMV(out,0,vp,0,new float[]{p[0],.95f,p[2],1},0);if(out[3]<=0)continue;float sx=vx+(out[0]/out[3]+1)*vw/2,sy=height-(vy+(out[1]/out[3]+1)*vh/2);float d=(float)Math.hypot(sx-x,sy-y);float radius=Math.max(app.dp(22),vh*.50f/out[3]);if(d<radius&&d<best){best=d;selected=i;}}}
    void loadScene(){try{String raw=app.getPreferences(0).getString("scene",null);if(raw==null){actors.add(new float[]{-.75f,0,0,20});actors.add(new float[]{1.0f,0,-1.2f,-25});return;}JSONArray a=new JSONArray(raw);for(int i=0;i<Math.min(24,a.length());i++){JSONArray p=a.getJSONArray(i);actors.add(new float[]{(float)p.getDouble(0),0,(float)p.getDouble(1),(float)p.getDouble(2)});}}catch(Exception e){actors.clear();actors.add(new float[]{0,0,0,0});}}
    void saveScene(){try{JSONArray a=new JSONArray();for(float[] p:actors){JSONArray v=new JSONArray();v.put((double)p[0]);v.put((double)p[2]);v.put((double)p[3]);a.put(v);}app.getPreferences(0).edit().putString("scene",a.toString()).apply();}catch(JSONException e){app.message("ذخیرهٔ چیدمان انجام نشد");}}

    @Override public void onSurfaceCreated(GL10 unused,javax.microedition.khronos.egl.EGLConfig config){
        String vs="uniform mat4 uMVP;uniform mat4 uModel;attribute vec3 aPosition;attribute vec3 aNormal;varying float vLight;varying float vDist;void main(){gl_Position=uMVP*vec4(aPosition,1.0);vec3 n=normalize(mat3(uModel)*aNormal);vLight=0.48+0.52*max(dot(n,normalize(vec3(-0.4,1.0,0.6))),0.0);vDist=gl_Position.w;}";
        String fs="precision mediump float;uniform vec4 uColor;varying float vLight;varying float vDist;void main(){vec3 c=uColor.rgb*vLight;float fog=clamp((vDist-12.0)/32.0,0.0,0.92);gl_FragColor=vec4(mix(c,vec3(0.08,0.11,0.15),fog),uColor.a);}";
        program=glCreateProgram();glAttachShader(program,shader(GL_VERTEX_SHADER,vs));glAttachShader(program,shader(GL_FRAGMENT_SHADER,fs));glLinkProgram(program);int[] ok=new int[1];glGetProgramiv(program,GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new RuntimeException(glGetProgramInfoLog(program));
        posLoc=glGetAttribLocation(program,"aPosition");normLoc=glGetAttribLocation(program,"aNormal");mvpLoc=glGetUniformLocation(program,"uMVP");modelLoc=glGetUniformLocation(program,"uModel");colorLoc=glGetUniformLocation(program,"uColor");
        cube=makeCube();sphere=makeSphere();floor=makeFloor();int[] tex=new int[1];glGenTextures(1,tex,0);texture=tex[0];glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
    }
    int shader(int type,String source){int id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id);int[] ok=new int[1];glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new RuntimeException(glGetShaderInfoLog(id));return id;}
    @Override public void onSurfaceChanged(GL10 unused,int w,int h){width=w;height=h;}
    void updateCamera(){
        tracking=false;
        if(camera&&session!=null){try{
            session.setCameraTextureName(texture);session.setDisplayGeometry(app.getWindowManager().getDefaultDisplay().getRotation(),width,height);
            Frame frame=session.update();com.google.ar.core.Camera cam=frame.getCamera();tracking=cam.getTrackingState()==TrackingState.TRACKING;
            if(tracking){cam.getDisplayOrientedPose().toMatrix(pose,0);if(recenter){float angle=(float)Math.toDegrees(Math.atan2(pose[8],pose[10]));Matrix.setIdentityM(arWorld,0);Matrix.translateM(arWorld,0,0,1.5f,5);Matrix.rotateM(arWorld,0,-angle,0,1,0);Matrix.translateM(arWorld,0,-pose[12],-pose[13],-pose[14]);recenter=false;}Matrix.multiplyMM(cameraWorld,0,arWorld,0,pose,0);Matrix.invertM(view,0,cameraWorld,0);}
            app.setStatus(tracking?"CAMERA · Tracking" : "CAMERA · Move slowly in a well-lit area");
        }catch(Exception e){app.setStatus("CAMERA · Tracking unavailable");}}
        if(!camera||recenter){float a=(float)Math.toRadians(yaw),b=(float)Math.toRadians(pitch);Matrix.setLookAtM(view,0,targetX+distance*(float)(Math.sin(a)*Math.cos(b)),targetY+distance*(float)Math.sin(b),targetZ+distance*(float)(Math.cos(a)*Math.cos(b)),targetX,targetY,targetZ,0,1,0);}
    }
    @Override public void onDrawFrame(GL10 unused){
        updateCamera();
        vw=width;vh=width*9/16;if(vh>height){vh=height;vw=height*16/9;}vx=(width-vw)/2;vy=(height-vh)/2;
        draw(width,height,vx,vy,vw,vh,false);
        if(recorder!=null){long now=System.nanoTime();if(now-lastFrameNs>=33_000_000L){
            EGLDisplay display=EGL14.eglGetCurrentDisplay();EGLContext ctx=EGL14.eglGetCurrentContext();EGLSurface oldDraw=EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW),oldRead=EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
            boolean failed=false;
            try{if(!EGL14.eglMakeCurrent(display,recordSurface,recordSurface,ctx))throw new RuntimeException("Encoder surface unavailable");draw(1280,720,0,0,1280,720,true);EGLExt.eglPresentationTimeANDROID(display,recordSurface,now);if(!EGL14.eglSwapBuffers(display,recordSurface))throw new RuntimeException("Encoder frame failed");lastFrameNs=now;recordedFrames++;}catch(Exception e){failed=true;}finally{EGL14.eglMakeCurrent(display,oldDraw,oldRead,ctx);}
            if(failed){app.message("ضبط به دلیل خطای گرافیکی متوقف شد");app.finishRecording();}
        }}
    }
    void draw(int sw,int sh,int x,int y,int w,int h,boolean exporting){
        glViewport(0,0,sw,sh);glClearColor(.04f,.06f,.08f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glEnable(GL_SCISSOR_TEST);glScissor(x,y,w,h);glClearColor(.08f,.11f,.15f,1);glClear(GL_COLOR_BUFFER_BIT);glDisable(GL_SCISSOR_TEST);glViewport(x,y,w,h);
        float fov=(float)Math.toDegrees(2*Math.atan((36f/(16f/9f))/(2*lens())));Matrix.perspectiveM(projection,0,fov,16f/9f,.06f,100);Matrix.multiplyMM(vp,0,projection,0,view,0);
        glUseProgram(program);glEnable(GL_DEPTH_TEST);glEnableVertexAttribArray(posLoc);glEnableVertexAttribArray(normLoc);
        Matrix.setIdentityM(model,0);drawMesh(floor,new float[]{.29f,.35f,.42f,1});
        for(int i=-20;i<=20;i++){part(cube,i,.004f,0,.014f,.007f,40,0,new float[]{.35f,.43f,.50f,1});part(cube,0,.004f,i,40,.007f,.014f,0,new float[]{.35f,.43f,.50f,1});}
        part(cube,0,.011f,0,40,.012f,.025f,0,new float[]{.62f,.28f,.28f,1});part(cube,0,.012f,0,.025f,.012f,40,0,new float[]{.27f,.48f,.64f,1});
        for(int i=0;i<actors.size();i++){float[] p=actors.get(i);float[] col=(!exporting&&i==selected)?green:white;
            // A neutral articulated stand-in: feet, lower and upper limbs, pelvis, torso, neck, head.
            bodyPart(p,cube,-.12f,.06f,.055f,.15f,.12f,.29f,col);bodyPart(p,cube,.12f,.06f,.055f,.15f,.12f,.29f,col);
            bodyPart(p,sphere,-.12f,.30f,0,.074f,.24f,.074f,col);bodyPart(p,sphere,.12f,.30f,0,.074f,.24f,.074f,col);
            bodyPart(p,sphere,-.12f,.69f,0,.09f,.24f,.09f,col);bodyPart(p,sphere,.12f,.69f,0,.09f,.24f,.09f,col);
            bodyPart(p,sphere,0,.94f,0,.21f,.16f,.12f,col);bodyPart(p,sphere,0,1.19f,0,.225f,.30f,.135f,col);
            bodyPart(p,sphere,-.29f,1.20f,0,.065f,.225f,.065f,col);bodyPart(p,sphere,.29f,1.20f,0,.065f,.225f,.065f,col);
            bodyPart(p,sphere,-.31f,.87f,0,.057f,.18f,.057f,col);bodyPart(p,sphere,.31f,.87f,0,.057f,.18f,.057f,col);
            bodyPart(p,sphere,0,1.49f,0,.07f,.09f,.07f,col);bodyPart(p,sphere,0,1.66f,0,.13f,.17f,.14f,col);
            bodyPart(p,sphere,0,1.65f,.132f,.035f,.035f,.03f,col);
        }
    }
    void bodyPart(float[] p,Mesh mesh,float x,float y,float z,float sx,float sy,float sz,float[] color){Matrix.setIdentityM(model,0);Matrix.translateM(model,0,p[0],0,p[2]);Matrix.rotateM(model,0,p[3],0,1,0);Matrix.translateM(model,0,x,y,z);Matrix.scaleM(model,0,sx,sy,sz);drawMesh(mesh,color);}
    void part(Mesh mesh,float x,float y,float z,float sx,float sy,float sz,float angle,float[] color){Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.rotateM(model,0,angle,0,1,0);Matrix.scaleM(model,0,sx,sy,sz);drawMesh(mesh,color);}
    void drawMesh(Mesh mesh,float[] color){Matrix.multiplyMM(mvp,0,vp,0,model,0);glUniformMatrix4fv(mvpLoc,1,false,mvp,0);glUniformMatrix4fv(modelLoc,1,false,model,0);glUniform4fv(colorLoc,1,color,0);mesh.data.position(0);glVertexAttribPointer(posLoc,3,GL_FLOAT,false,24,mesh.data);mesh.data.position(3);glVertexAttribPointer(normLoc,3,GL_FLOAT,false,24,mesh.data);glDrawArrays(GL_TRIANGLES,0,mesh.count);}
    boolean isRecording(){return recorder!=null;}
    void startRecording(boolean audio) throws Exception {
        if(recorder!=null)return;
        try{
            ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,"FilmSpace_"+new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".mp4");values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/FilmSpace");values.put(MediaStore.Video.Media.IS_PENDING,1);
            videoUri=app.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);if(videoUri==null)throw new Exception("Cannot create video");videoFd=app.getContentResolver().openFileDescriptor(videoUri,"w");
            recorder=new MediaRecorder();if(audio)recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setOutputFile(videoFd.getFileDescriptor());recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);recorder.setVideoSize(1280,720);recorder.setVideoFrameRate(30);recorder.setVideoEncodingBitRate(6_000_000);if(audio){recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioEncodingBitRate(128000);recorder.setAudioSamplingRate(44100);}recorder.prepare();
            encoderSurface=recorder.getSurface();recordDisplay=EGL14.eglGetCurrentDisplay();EGLContext context=EGL14.eglGetCurrentContext();int[] id=new int[1];EGL14.eglQueryContext(recordDisplay,context,EGL14.EGL_CONFIG_ID,id,0);EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];EGL14.eglChooseConfig(recordDisplay,new int[]{EGL14.EGL_CONFIG_ID,id[0],EGL14.EGL_NONE},0,configs,0,1,count,0);recordSurface=EGL14.eglCreateWindowSurface(recordDisplay,configs[0],encoderSurface,new int[]{EGL14.EGL_NONE},0);if(recordSurface==null||recordSurface==EGL14.EGL_NO_SURFACE)throw new Exception("Cannot create encoder surface");
            recorder.start();startNs=System.nanoTime();lastFrameNs=0;recordedFrames=0;
        }catch(Exception e){releaseRecorder();if(videoUri!=null){app.getContentResolver().delete(videoUri,null,null);videoUri=null;}throw e;}
    }
    Uri stopRecording(){if(recorder==null)return null;Uri result=videoUri;boolean ok=false;try{recorder.stop();ok=recordedFrames>0;}catch(RuntimeException e){app.message("ضبط خیلی کوتاه بود یا کامل نشد؛ دوباره امتحان کن");}finally{releaseRecorder();}
        if(result!=null){try{if(ok){ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.IS_PENDING,0);app.getContentResolver().update(result,v,null,null);}else{app.getContentResolver().delete(result,null,null);result=null;}}catch(Exception e){app.message("ذخیره ویدیو کامل نشد");result=null;}}videoUri=null;return result;
    }
    void releaseRecorder(){if(recordSurface!=null&&recordSurface!=EGL14.EGL_NO_SURFACE&&recordDisplay!=null)EGL14.eglDestroySurface(recordDisplay,recordSurface);recordSurface=EGL14.EGL_NO_SURFACE;if(encoderSurface!=null){encoderSurface.release();encoderSurface=null;}if(recorder!=null){try{recorder.release();}catch(Exception ignored){}recorder=null;}if(videoFd!=null){try{videoFd.close();}catch(Exception ignored){}videoFd=null;}}
}
