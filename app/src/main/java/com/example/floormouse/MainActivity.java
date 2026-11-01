package com.example.floormouse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.provider.Settings;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS=8;
    private HidMouseController hid;
    private TextView status, motion, speedText;
    private TextView pauseButton, torchButton, flipXButton, flipYButton;
    private final MotionTracker tracker=new MotionTracker();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraManager cameraManager;
    private CameraDevice camera;
    private CameraCaptureSession cameraSession;
    private CaptureRequest.Builder request;
    private ImageReader reader;
    private TextureView previewView;
    private Surface previewSurface;
    private float smoothX=0f, smoothY=0f;
    private int missingFrames=0;
    private final AtomicBoolean cameraStarting=new AtomicBoolean();
    private volatile boolean paused=false, torch=true, flipX=false, flipY=false;
    private volatile float speed=2.5f;
    private float residualX=0, residualY=0;
    private int frames=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status=label("마우스 준비 중...",false);
        motion=label("카메라 준비 중...",false);
        hid=new HidMouseController(this, msg -> status.setText(msg));
        createUi();
        ensurePermissions();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,boolean title){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(title?22:14);t.setTextColor(Color.rgb(27,43,63));
        t.setPadding(0,dp(6),0,dp(9));return t;
    }
    private Button button(String text,Runnable action) {
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);
        b.setOnClickListener(v->action.run());return b;
    }
    private void add(LinearLayout layout,android.view.View v){
        layout.addView(v,new LinearLayout.LayoutParams(-1,-2));
    }
    private static final int DARK = 0xFF0B1220;
    private static final int PANEL = 0xFF182338;
    private static final int PANEL_SOFT = 0xFF202F47;
    private static final int ACCENT = 0xFF399AFF;
    private static final int WHITE = 0xFFF5F9FF;
    private static final int MUTED = 0xFFA3B5CB;

    private TextView heading(String message, int textSize, int color) {
        TextView v=new TextView(this);
        v.setText(message);
        v.setTextSize(textSize);
        v.setTextColor(color);
        v.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return v;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private TextView pill(String message, int color, Runnable action) {
        TextView v=heading(message, 14, WHITE);
        v.setGravity(android.view.Gravity.CENTER);
        v.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        v.setMinHeight(dp(50));
        v.setBackground(rounded(color, 13));
        v.setOnClickListener(view -> action.run());
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private LinearLayout card() {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(14),dp(16),dp(16));
        android.graphics.drawable.GradientDrawable b=rounded(PANEL, 18);
        b.setStroke(dp(1),0xFF2F415A);
        card.setBackground(b);
        return card;
    }

    private void verticalGap(LinearLayout layout, int size) {
        android.view.View v=new android.view.View(this);
        layout.addView(v,new LinearLayout.LayoutParams(1,dp(size)));
    }

    private void pairRow(LinearLayout parent, TextView left, TextView right) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,dp(50),1);
        l.setMargins(0,0,dp(5),0);
        row.addView(left,l);
        LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(0,dp(50),1);
        r.setMargins(dp(5),0,0,0);
        row.addView(right,r);
        add(parent,row);
    }

    private FrameLayout tabPages;
    private LinearLayout mousePage;
    private ScrollView settingsPage;
    private TextView mouseTab;
    private TextView settingsTab;
    private CustomButtonPanel mousePanel;

    private TextView tabButton(String title){
        TextView v=heading(title,16,WHITE);
        v.setGravity(android.view.Gravity.CENTER);
        v.setMinHeight(dp(50));
        v.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
            android.graphics.Typeface.NORMAL));
        return v;
    }

    private void showTab(boolean settings) {
        // Keep both pages in the view tree. This lets the camera TextureView
        // stay attached while the mouse page covers the settings page.
        if(settings) settingsPage.bringToFront();
        else mousePage.bringToFront();
        tabPages.invalidate();
        mouseTab.setTextColor(settings?MUTED:WHITE);
        settingsTab.setTextColor(settings?WHITE:MUTED);
        mouseTab.setBackground(rounded(settings?PANEL:ACCENT,12));
        settingsTab.setBackground(rounded(settings?ACCENT:PANEL,12));
    }

    private void createUi(){
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout root=new LinearLayout(this);
        root.setBackgroundColor(DARK);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        LinearLayout header=new LinearLayout(this);
        header.setPadding(dp(18),dp(8),dp(18),dp(4));
        header.setOrientation(LinearLayout.VERTICAL);
        add(header,heading("바닥 마우스",24,WHITE));
        add(root,header);

        LinearLayout tabs=new LinearLayout(this);
        tabs.setPadding(dp(12),dp(5),dp(12),dp(9));
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        mouseTab=tabButton("🖱 마우스");
        settingsTab=tabButton("⚙ 설정");
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(50),1);
        left.setMargins(0,0,dp(5),0);
        tabs.addView(mouseTab,left);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(50),1);
        right.setMargins(dp(5),0,0,0);
        tabs.addView(settingsTab,right);
        add(root,tabs);

        tabPages=new FrameLayout(this);
        root.addView(tabPages,new LinearLayout.LayoutParams(-1,0,1));

        settingsPage=new ScrollView(this);
        settingsPage.setBackgroundColor(DARK);
        settingsPage.setFillViewport(true);
        settingsPage.setClickable(true);
        LinearLayout settingsRoot=new LinearLayout(this);
        settingsRoot.setOrientation(LinearLayout.VERTICAL);
        settingsRoot.setPadding(dp(18),dp(16),dp(18),dp(28));
        settingsPage.addView(settingsRoot);
        tabPages.addView(settingsPage,new FrameLayout.LayoutParams(-1,-1));

        add(settingsRoot,heading("환경 설정",22,WHITE));
        verticalGap(settingsRoot,5);
        add(settingsRoot,heading("연결과 카메라, 버튼 배치를 여기서 관리하세요.",13,MUTED));
        verticalGap(settingsRoot,16);

        status.setTextColor(0xFFB9DCFF);
        status.setTextSize(14);
        status.setPadding(dp(14),dp(12),dp(14),dp(12));
        status.setBackground(rounded(PANEL_SOFT,12));
        add(settingsRoot,status);
        verticalGap(settingsRoot,12);

        LinearLayout connection=card();
        add(connection,heading("블루투스 연결",17,WHITE));
        verticalGap(connection,12);
        pairRow(connection,
            pill("① 마우스 등록",ACCENT,()->{ensurePermissions();hid.start();}),
            pill("② 기기 연결",0xFF314862,this::chooseDevice));
        verticalGap(connection,10);
        pairRow(connection,
            pill("기기 검색 허용",0xFF29394E,this::discoverable),
            pill("커서 이동 시험",0xFF29394E,()->{
                if(hid.isConnected()) hid.move(60,0);
                else status.setText("먼저 다른 기기와 연결해 주세요.");
            }));
        add(settingsRoot,connection);
        verticalGap(settingsRoot,12);

        LinearLayout cameraCard=card();
        add(cameraCard,heading("📷 실시간 바닥 카메라",17,WHITE));
        verticalGap(cameraCard,10);
        previewView=new TextureView(this);
        previewView.setOpaque(true);
        previewView.setBackground(rounded(0xFF101923,13));
        previewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int width,int height){
                if(hasPermissions())startCamera();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width,int height){}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){return true;}
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface){}
        });
        cameraCard.addView(previewView,new LinearLayout.LayoutParams(-1,dp(170)));
        verticalGap(cameraCard,7);
        add(cameraCard,heading("바닥을 촬영하는 화면입니다. 마우스 탭에서도 카메라는 계속 움직임을 추적합니다.",12,MUTED));
        add(settingsRoot,cameraCard);
        verticalGap(settingsRoot,12);

        LinearLayout options=card();
        add(options,heading("마우스 움직임",17,WHITE));
        verticalGap(options,12);
        pairRow(options,
            torchButton=pill("🔦 손전등 켜짐",0xFF314862,()->{
                torch=!torch;
                torchButton.setText(torch?"🔦 손전등 켜짐":"🔦 손전등 꺼짐");
                applyCapture();
            }),
            pauseButton=pill("⏸ 이동 일시정지",0xFF314862,()->{
                paused=!paused;tracker.reset();
                smoothX=0;smoothY=0;residualX=0;residualY=0;missingFrames=0;
                pauseButton.setText(paused?"▶ 이동 다시 시작":"⏸ 이동 일시정지");
            }));
        verticalGap(options,14);
        speedText=heading("커서 속도 · 2.5배",14,WHITE);
        add(options,speedText);
        SeekBar seek=new SeekBar(this);
        seek.setMax(55);
        seek.setProgress(20);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int p,boolean fromUser){
                speed=(p+5)/10f;
                speedText.setText(String.format(Locale.KOREA,"커서 속도 · %.1f배",speed));
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        add(options,seek);
        verticalGap(options,5);
        motion.setTextColor(MUTED);
        motion.setTextSize(13);
        add(options,motion);
        verticalGap(options,9);
        LinearLayout advanced=new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(android.view.View.GONE);
        pairRow(advanced,
            flipXButton=pill("가로 반전 · 꺼짐",0xFF29394E,()->{
                flipX=!flipX;flipXButton.setText("가로 반전 · "+(flipX?"켜짐":"꺼짐"));
            }),
            flipYButton=pill("세로 반전 · 꺼짐",0xFF29394E,()->{
                flipY=!flipY;flipYButton.setText("세로 반전 · "+(flipY?"켜짐":"꺼짐"));
            }));
        TextView more=pill("방향 조정 펼치기  ▾",0xFF24334A,()->{});
        more.setOnClickListener(v->{
            boolean visible=advanced.getVisibility()==android.view.View.VISIBLE;
            advanced.setVisibility(visible?android.view.View.GONE:android.view.View.VISIBLE);
            more.setText(visible?"방향 조정 펼치기  ▾":"방향 조정 접기  ▴");
        });
        add(options,more);
        verticalGap(options,9);
        add(options,advanced);
        add(settingsRoot,options);
        verticalGap(settingsRoot,12);

        LinearLayout buttonsCard=card();
        add(buttonsCard,heading("사용자 버튼 관리",17,WHITE));
        verticalGap(buttonsCard,8);
        add(buttonsCard,heading("버튼을 추가하거나 편집하면 마우스 탭에 배치됩니다.",12,MUTED));
        verticalGap(buttonsCard,12);
        TextView editButton=pill("✎ 버튼 편집",0xFF314862,()->{});
        editButton.setOnClickListener(v->{
            mousePanel.setEditing(!mousePanel.isEditing());
            editButton.setText(mousePanel.isEditing()?"✓ 편집 완료":"✎ 버튼 편집");
            status.setText(mousePanel.isEditing()
                ?"편집 모드: 마우스 화면에서 추가 버튼을 끌어 이동하거나 눌러 수정하세요."
                :"버튼 배치를 저장했습니다.");
            if(mousePanel.isEditing())showTab(false);
        });
        pairRow(buttonsCard,
            pill("＋ 버튼 추가",ACCENT,()->mousePanel.addButton()),
            editButton);
        verticalGap(buttonsCard,8);
        add(buttonsCard,heading("편집을 누른 뒤 마우스 탭에서 버튼을 이동하고, 설정에서 편집 완료를 눌러주세요.",12,MUTED));
        add(settingsRoot,buttonsCard);
        verticalGap(settingsRoot,16);
        add(settingsRoot,heading("카메라를 바닥에서 5~15cm 정도 띄워 보세요. 무늬 있는 매트에서 더 잘 인식합니다. 기기에 따라 블루투스 마우스 기능이 지원되지 않을 수 있습니다.",12,MUTED));

        mousePage=new LinearLayout(this);
        mousePage.setOrientation(LinearLayout.VERTICAL);
        mousePage.setBackgroundColor(DARK);
        mousePage.setPadding(dp(10),dp(4),dp(10),dp(14));
        mousePage.setClickable(true);
        mousePanel=new CustomButtonPanel(this,hid);
        mousePage.addView(mousePanel,new LinearLayout.LayoutParams(-1,0,1));
        tabPages.addView(mousePage,new FrameLayout.LayoutParams(-1,-1));

        mouseTab.setOnClickListener(v->showTab(false));
        settingsTab.setOnClickListener(v->showTab(true));
        showTab(false);
    }
    private boolean hasPermissions(){
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return false;
        if(Build.VERSION.SDK_INT>=31){
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
                 &&checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    private void ensurePermissions(){
        if(!hasPermissions()){
            ArrayList<String> p=new ArrayList<>();
            p.add(Manifest.permission.CAMERA);
            if(Build.VERSION.SDK_INT>=31){
                p.add(Manifest.permission.BLUETOOTH_CONNECT);
                p.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            requestPermissions(p.toArray(new String[0]),REQUEST_PERMISSIONS);
        } else {startCamera();hid.start();}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==REQUEST_PERMISSIONS){
            if(hasPermissions()){startCamera();hid.start();}
            else status.setText("카메라와 주변 기기 권한을 허용해야 합니다.");
        }
    }
    @SuppressLint("MissingPermission")
    private void discoverable(){
        if(!hasPermissions()){ensurePermissions();return;}
        try{
            Intent i=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120);
            startActivityForResult(i,17);
        }catch(Exception e){status.setText("검색 허용 실패: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code==17&&result>0)status.setText("다른 기기의 블루투스 설정에서 이 휴대폰을 선택하세요.");
    }
    private void chooseDevice(){
        if(!hasPermissions()){ensurePermissions();return;}
        List<BluetoothDevice> paired=hid.paired();
        if(paired.isEmpty()){
            new AlertDialog.Builder(this).setTitle("페어링된 기기 없음")
            .setMessage("연결할 컴퓨터나 갤럭시 탭과 먼저 블루투스 페어링을 하세요.")
            .setPositiveButton("블루투스 설정",(d,w)->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
            .setNegativeButton("닫기",null).show();return;
        }
        String[] names=new String[paired.size()];
        for(int i=0;i<names.length;i++)names[i]=hid.name(paired.get(i));
        new AlertDialog.Builder(this).setTitle("연결할 기기 선택")
           .setItems(names,(d,which)->hid.connect(paired.get(which)))
           .setNegativeButton("닫기",null).show();
    }
    @SuppressLint("MissingPermission")
    private void startCamera(){
        if(!hasPermissions() || previewView==null || !previewView.isAvailable())return;
        if(!cameraStarting.compareAndSet(false,true))return;
        cameraThread=new HandlerThread("floor-camera");
        cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());
        cameraManager=(CameraManager)getSystemService(CAMERA_SERVICE);
        try{
            String id=null; boolean flash=false;
            for(String candidate:cameraManager.getCameraIdList()){
                CameraCharacteristics ch=cameraManager.getCameraCharacteristics(candidate);
                Integer facing=ch.get(CameraCharacteristics.LENS_FACING);
                if(facing!=null&&facing==CameraCharacteristics.LENS_FACING_BACK){
                    Boolean hasFlash=ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if(id==null||(!flash&&Boolean.TRUE.equals(hasFlash))){
                        id=candidate;flash=Boolean.TRUE.equals(hasFlash);
                    }
                }
            }
            if(id==null){motion.setText("후면 카메라를 찾을 수 없습니다.");cameraStarting.set(false);return;}
            StreamConfigurationMap map=cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size chosen=new Size(640,480);
            if(map!=null&&map.getOutputSizes(ImageFormat.YUV_420_888)!=null){
                long min=Long.MAX_VALUE;
                for(Size size:map.getOutputSizes(ImageFormat.YUV_420_888)){
                    long diff=Math.abs((long)size.getWidth()*size.getHeight()-640L*480L);
                    if(diff<min){chosen=size;min=diff;}
                }
            }
            reader=ImageReader.newInstance(chosen.getWidth(),chosen.getHeight(),ImageFormat.YUV_420_888,3);
            reader.setOnImageAvailableListener(r->{
                Image image=null;
                try{image=r.acquireLatestImage();if(image!=null)analyse(image);}
                catch(Exception ignored){}
                finally{if(image!=null)image.close();}
            },cameraHandler);
            cameraManager.openCamera(id,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice c){
                    camera=c;
                    try{
                        SurfaceTexture texture=previewView.getSurfaceTexture();
                        if(texture==null){cameraStarting.set(false);return;}
                        texture.setDefaultBufferSize(reader.getWidth(),reader.getHeight());
                        previewSurface=new Surface(texture);
                        c.createCaptureSession(Arrays.asList(reader.getSurface(),previewSurface),
                            new CameraCaptureSession.StateCallback(){
                                @Override public void onConfigured(CameraCaptureSession session){
                                    cameraSession=session;
                                    try{
                                        request=c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                        request.addTarget(reader.getSurface());
                                        request.addTarget(previewSurface);
                                        request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                        request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
                                        applyCapture();
                                        ui.post(()->motion.setText("카메라 준비 완료. 바닥을 비춰 움직여 보세요."));
                                    }catch(Exception e){cameraError(e);}
                                }
                                @Override public void onConfigureFailed(CameraCaptureSession s){
                                    ui.post(()->motion.setText("카메라 세션 설정 실패"));
                                }
                            },cameraHandler);
                    }catch(Exception e){cameraError(e);}
                }
                @Override public void onDisconnected(CameraDevice c){c.close();camera=null;}
                @Override public void onError(CameraDevice c,int error){
                    c.close();camera=null;
                    ui.post(()->motion.setText("카메라 오류 번호: "+error));
                }
            },cameraHandler);
        }catch(Exception e){cameraStarting.set(false);cameraError(e);}
    }
    private void cameraError(Exception e){
        ui.post(()->motion.setText("카메라 오류: "+e.getMessage()));
    }
    private void applyCapture(){
        if(cameraSession==null||request==null)return;
        try{
            request.set(CaptureRequest.FLASH_MODE,torch?CaptureRequest.FLASH_MODE_TORCH:CaptureRequest.FLASH_MODE_OFF);
            cameraSession.setRepeatingRequest(request.build(),null,cameraHandler);
        }catch(Exception e){cameraError(e);}
    }
    private void analyse(Image image){
        Image.Plane plane=image.getPlanes()[0];
        ByteBuffer buf=plane.getBuffer();
        int width=image.getWidth(),height=image.getHeight();
        int stride=plane.getRowStride(),step=plane.getPixelStride();
        byte[] gray=new byte[MotionTracker.WIDTH*MotionTracker.HEIGHT];
        for(int y=0;y<MotionTracker.HEIGHT;y++){
            int sy=(int)(height*(0.15+(y+0.5)*0.7/MotionTracker.HEIGHT));
            for(int x=0;x<MotionTracker.WIDTH;x++){
                int sx=(int)(width*(0.15+(x+0.5)*0.7/MotionTracker.WIDTH));
                gray[y*MotionTracker.WIDTH+x]=buf.get(sy*stride+sx*step);
            }
        }
        MotionTracker.Motion m=tracker.track(gray);
        if(++frames%20==0){
            String s=m==null?"바닥 무늬 추적 중: 움직임 없음":"감지 이동: 가로 "+m.dx+", 세로 "+m.dy;
            ui.post(()->motion.setText(s));
        }
        // Apply a low-pass filter and bridge only a very brief loss of camera frames.
        // Stop quickly on prolonged tracking loss so the pointer cannot drift.
        if(m!=null){
            float rawX=(flipX?-m.dx:m.dx)*speed;
            float rawY=(flipY?-m.dy:m.dy)*speed;
            smoothX=smoothX*0.52f+rawX*0.48f;
            smoothY=smoothY*0.52f+rawY*0.48f;
            missingFrames=0;
        } else {
            missingFrames++;
            if(missingFrames<=2){
                smoothX*=0.6f; smoothY*=0.6f;
            } else {
                smoothX=0f; smoothY=0f;
            }
        }
        if(paused||!hid.isConnected()){
            residualX=0;residualY=0;return;
        }
        float x=smoothX+residualX;
        float y=smoothY+residualY;
        int dx=Math.max(-127,Math.min(127,Math.round(x)));
        int dy=Math.max(-127,Math.min(127,Math.round(y)));
        residualX=x-dx;residualY=y-dy;
        if(dx!=0 || dy!=0) hid.move(dx,dy);
    }
    @Override protected void onDestroy(){
        try{if(cameraSession!=null)cameraSession.close();}catch(Exception ignored){}
        try{if(camera!=null)camera.close();}catch(Exception ignored){}
        try{if(reader!=null)reader.close();}catch(Exception ignored){}
        try{if(previewSurface!=null)previewSurface.release();}catch(Exception ignored){}
        if(cameraThread!=null){cameraThread.quitSafely();}
        hid.close();super.onDestroy();
    }
}
