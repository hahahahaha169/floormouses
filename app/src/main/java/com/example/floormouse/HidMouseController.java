package com.example.floormouse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class HidMouseController {
    interface Status { void show(String text); }
    private final Context context;
    private final Status status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    final BluetoothAdapter adapter;
    private volatile BluetoothHidDevice profile;
    private volatile BluetoothDevice connected;
    private volatile boolean registered;
    private boolean connecting;
    private int buttons;
    private final Object lock = new Object();

    // HID mouse: buttons + relative X/Y + scroll wheel.
    private static final byte[] DESCRIPTOR = new byte[] {
        0x05,0x01, 0x09,0x02, (byte)0xA1,0x01, 0x09,0x01,(byte)0xA1,0x00,
        0x05,0x09, 0x19,0x01, 0x29,0x03, 0x15,0x00, 0x25,0x01,
        (byte)0x95,0x03,0x75,0x01,(byte)0x81,0x02,
        (byte)0x95,0x01,0x75,0x05,(byte)0x81,0x03,
        0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x38,
        0x15,(byte)0x81,0x25,0x7f,0x75,0x08,(byte)0x95,0x03,(byte)0x81,0x06,
        (byte)0xC0,(byte)0xC0
    };
    HidMouseController(Context c, Status s) {
        context=c; status=s;
        BluetoothManager mgr=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter=mgr==null?null:mgr.getAdapter();
    }
    private void message(String m) { handler.post(() -> status.show(m)); }
    boolean isConnected() { return connected != null; }
    private boolean permission() {
        return Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    }
    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice device, boolean success) {
            registered=success;
            if (!success) connected=null;
            message(success?"마우스 등록 완료. 다른 기기에서 페어링하세요.":"마우스 등록 해제됨. 다시 등록하세요.");
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if(state==BluetoothProfile.STATE_CONNECTED) {
                connected=device; message("연결됨: "+name(device));
            } else if(state==BluetoothProfile.STATE_DISCONNECTED) {
                connected=null; message("연결이 끊어졌습니다.");
            } else if(state==BluetoothProfile.STATE_CONNECTING) message("연결 중...");
        }
        @Override public void onGetReport(BluetoothDevice device, byte type, byte id, int length) {
            BluetoothHidDevice p=profile;
            if(p==null) return;
            try {
                if(type==BluetoothHidDevice.REPORT_TYPE_INPUT && id==0)
                    p.replyReport(device,type,id,new byte[]{(byte)buttons,0,0,0});
                else p.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);
            }catch(Exception e) { message("보고서 처리 오류: "+e.getMessage()); }
        }
    };
    private final BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int type, BluetoothProfile service) {
            if(type==BluetoothProfile.HID_DEVICE && service instanceof BluetoothHidDevice) {
                profile=(BluetoothHidDevice)service; register();
            }
        }
        @Override public void onServiceDisconnected(int type) {
            if(type==BluetoothProfile.HID_DEVICE) {
                profile=null; registered=false; connected=null; connecting=false;
                message("마우스 서비스 연결이 종료되었습니다.");
            }
        }
    };
    @SuppressLint("MissingPermission")
    void start() {
        if(!permission()) { message("주변 기기 권한이 필요합니다."); return; }
        if(adapter==null || !adapter.isEnabled()) { message("휴대폰 블루투스를 켜 주세요."); return; }
        if(profile!=null) { if(!registered) register(); return; }
        if(connecting) return;
        connecting=true;
        message("블루투스 마우스 등록 중...");
        try {
            if(!adapter.getProfileProxy(context,listener,BluetoothProfile.HID_DEVICE)) {
                connecting=false; message("이 기기에서는 블루투스 마우스 기능을 지원하지 않을 수 있습니다.");
            }
        }catch(Exception e) { connecting=false; message("등록 오류: "+e.getMessage()); }
    }
    @SuppressLint("MissingPermission")
    private void register() {
        BluetoothHidDevice p=profile; if(p==null || !permission()) return;
        try {
            BluetoothHidDeviceAppSdpSettings s=new BluetoothHidDeviceAppSdpSettings(
                "Floor Mouse","Camera optical mouse","FloorMouse",BluetoothHidDevice.SUBCLASS1_MOUSE,DESCRIPTOR);
            if(!p.registerApp(s,null,null,executor,callback)) message("블루투스 마우스 등록 요청이 거부되었습니다.");
        }catch(Exception e) { message("마우스 등록 실패: "+e.getMessage()); }
    }
    @SuppressLint("MissingPermission")
    List<BluetoothDevice> paired() {
        if(!permission() || adapter==null) return new ArrayList<>();
        try { return new ArrayList<>(adapter.getBondedDevices()); }
        catch(Exception e){ return new ArrayList<>(); }
    }
    @SuppressLint("MissingPermission")
    String name(BluetoothDevice d) {
        try { String n=d.getName(); return n==null?d.getAddress():n; }
        catch(Exception e){return "대상 기기";}
    }
    @SuppressLint("MissingPermission")
    void connect(BluetoothDevice d) {
        if(!registered || profile==null) { message("마우스를 먼저 등록해 주세요.");return; }
        try {
            if(!profile.connect(d)) message("기기 연결 요청 실패. 페어링 여부를 확인하세요.");
            else message("연결 요청 중: "+name(d));
        }catch(Exception e){message("연결 오류: "+e.getMessage());}
    }
    @SuppressLint("MissingPermission")
    private boolean report(int x,int y,int wheel) {
        BluetoothHidDevice p=profile; BluetoothDevice d=connected;
        if(p==null || d==null) return false;
        try {
            return p.sendReport(d,0,new byte[]{(byte)buttons,(byte)Math.max(-127,Math.min(127,x)),
                (byte)Math.max(-127,Math.min(127,y)),(byte)wheel});
        }catch(Exception e){return false;}
    }
    void move(int x,int y) { synchronized(lock){report(x,y,0);} }
    // 1=left, 2=right, 4=wheel. Keep the button held for dragging.
    void buttonDown(int button) {
        if(button != 1 && button != 2 && button != 4) return;
        synchronized(lock) {
            buttons |= button;
            report(0,0,0);
        }
    }
    void buttonUp(int button) {
        if(button != 1 && button != 2 && button != 4) return;
        synchronized(lock) {
            buttons &= ~button;
            report(0,0,0);
        }
    }

    void click(boolean right) {
        int mask=right?2:1;
        synchronized(lock){buttons|=mask;report(0,0,0);}
        handler.postDelayed(() -> {synchronized(lock){buttons&=~mask;report(0,0,0);}},90);
    }
    void wheel(boolean up){synchronized(lock){report(0,0,up?1:-1);report(0,0,0);}}
    @SuppressLint("MissingPermission")
    void close(){
        try{if(profile!=null){profile.unregisterApp();adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,profile);}}
        catch(Exception ignored){}
        connected=null;profile=null;registered=false;executor.shutdown();
    }
}
