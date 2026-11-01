package com.example.floormouse;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** User-configurable on-screen buttons placed on top of the mouse graphic. */
final class CustomButtonPanel extends FrameLayout {
    private static final String PREFS = "floor_mouse_buttons";
    private static final String STORAGE_KEY = "layout_v1";
    private static final String[] ACTIONS = {"왼쪽 클릭", "오른쪽 클릭", "가운데 클릭", "위로 스크롤", "아래로 스크롤"};
    private static final String[] SHORT = {"왼쪽", "오른쪽", "중간", "휠 ↑", "휠 ↓"};
    private final SharedPreferences prefs;
    private final HidMouseController hid;
    private final List<Model> models = new ArrayList<>();
    private final MouseSurfaceView mouse;
    private boolean editing;

    private static final class Model {
        int type;
        int width = 90; // dp
        String label;
        float x, y;     // fraction of parent width and height
        TextView view;
    }

    CustomButtonPanel(Context context, HidMouseController controller) {
        super(context);
        hid = controller;
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setClipChildren(false);
        mouse = new MouseSurfaceView(context, new MouseSurfaceView.Listener() {
            @Override public void buttonDown(int button) { hid.buttonDown(button); }
            @Override public void buttonUp(int button) { hid.buttonUp(button); }
            @Override public void wheel(int steps) { scroll(steps); }
        });
        mouse.setOnTouchListener((v, e) -> editing);
        addView(mouse, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        restore();
    }

    boolean isEditing() { return editing; }
    void setEditing(boolean value) {
        editing = value;
        for (Model m : models) decorate(m);
        invalidate();
    }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void scroll(int ticks) {
        for(int i=0; i<Math.min(8,Math.abs(ticks)); i++) hid.wheel(ticks>0);
    }

    private void restore() {
        try {
            JSONArray list=new JSONArray(prefs.getString(STORAGE_KEY,"[]"));
            for(int i=0;i<list.length();i++) {
                JSONObject o=list.getJSONObject(i);
                Model m=new Model();
                m.type=Math.max(0,Math.min(4,o.optInt("type",0)));
                m.width=Math.max(65,Math.min(150,o.optInt("width",90)));
                m.x=(float)o.optDouble("x",0.3);
                m.y=(float)o.optDouble("y",0.7);
                m.label=o.optString("label",SHORT[m.type]);
                if(m.label.trim().isEmpty()) m.label=SHORT[m.type];
                models.add(m);
                createView(m);
            }
        }catch(Exception ignored){}
    }

    private void save() {
        JSONArray arr=new JSONArray();
        for(Model m:models) {
            JSONObject o=new JSONObject();
            try {
                o.put("type",m.type); o.put("width",m.width);
                o.put("x",(double)m.x); o.put("y",(double)m.y); o.put("label",m.label);
                arr.put(o);
            }catch(Exception ignored){}
        }
        prefs.edit().putString(STORAGE_KEY,arr.toString()).apply();
    }

    void addButton() {
        new AlertDialog.Builder(getContext())
            .setTitle("어떤 버튼을 추가할까요?")
            .setItems(ACTIONS,(dialog,which)->{
                Model m=new Model();
                m.type=which;
                m.label=SHORT[which];
                m.x=0.12f+0.18f*(models.size()%3);
                m.y=0.62f+0.13f*((models.size()/3)%2);
                models.add(m);
                createView(m);
                save();
            })
            .setNegativeButton("취소",null).show();
    }

    private void decorate(Model m) {
        TextView v=m.view;
        if(v==null) return;
        v.setText(editing?"✎ "+m.label:m.label);
        GradientDrawable b=new GradientDrawable();
        b.setColor(editing?0xFF0F766E:0xFF1D4D82);
        b.setStroke(dp(1.5f),editing?0xFF5EEAD4:0xFF7DC6FF);
        b.setCornerRadius(dp(14));
        v.setBackground(b);
    }

    private void createView(Model m) {
        TextView v=new TextView(getContext());
        m.view=v;
        v.setTextColor(Color.WHITE);
        v.setTextSize(12);
        v.setGravity(Gravity.CENTER);
        v.setMaxLines(2);
        v.setPadding(dp(5),0,dp(5),0);
        v.setElevation(dp(7));
        decorate(m);
        addView(v,new FrameLayout.LayoutParams(dp(m.width),dp(48)));
        v.setOnTouchListener(new View.OnTouchListener() {
            private float startRawX,startRawY,startX,startY;
            private boolean dragged;
            @Override public boolean onTouch(View view,MotionEvent event) {
                int a=event.getActionMasked();
                if(a==MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    startRawX=event.getRawX();startRawY=event.getRawY();
                    startX=view.getX();startY=view.getY();dragged=false;
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if(!editing) perform(m,true);
                    return true;
                }
                if(a==MotionEvent.ACTION_MOVE) {
                    float dx=event.getRawX()-startRawX;
                    float dy=event.getRawY()-startRawY;
                    if(editing) {
                        if(Math.abs(dx)>dp(7)||Math.abs(dy)>dp(7))dragged=true;
                        view.setX(Math.max(0,Math.min(getWidth()-view.getWidth(),startX+dx)));
                        view.setY(Math.max(0,Math.min(getHeight()-view.getHeight(),startY+dy)));
                    }
                    return true;
                }
                if(a==MotionEvent.ACTION_UP || a==MotionEvent.ACTION_CANCEL) {
                    if(editing) {
                        m.x=view.getX()/Math.max(1,getWidth());
                        m.y=view.getY()/Math.max(1,getHeight());
                        save();
                        if(!dragged && a==MotionEvent.ACTION_UP)edit(m);
                    } else perform(m,false);
                    return true;
                }
                return true;
            }
        });
        post(()->place(m));
    }

    private void place(Model m) {
        if(m.view==null || getWidth()<=0 || getHeight()<=0)return;
        m.view.setX(Math.max(0,Math.min(getWidth()-m.view.getWidth(),m.x*getWidth())));
        m.view.setY(Math.max(0,Math.min(getHeight()-m.view.getHeight(),m.y*getHeight())));
    }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        post(()->{for(Model m:models)place(m);});
    }

    private void perform(Model m,boolean down) {
        if(m.type<3) {
            hidButton(m.type,down);
        } else if(down) {
            scroll(m.type==3?1:-1);
        }
    }
    private void hidButton(int type,boolean down) {
        int key=type==0?1:type==1?2:4;
        if(down)hid.buttonDown(key);
        else hid.buttonUp(key);
    }

    private void edit(Model m) {
        String[] options={"기능 변경","이름 변경","크기 변경","이 버튼 삭제"};
        new AlertDialog.Builder(getContext()).setTitle("버튼 편집: "+m.label)
            .setItems(options,(dialog,which)->{
                if(which==0) {
                    new AlertDialog.Builder(getContext()).setTitle("버튼 기능")
                        .setSingleChoiceItems(ACTIONS,m.type,(d,pos)->{
                            m.type=pos;m.label=SHORT[pos];decorate(m);save();d.dismiss();
                        }).setNegativeButton("취소",null).show();
                } else if(which==1)rename(m);
                else if(which==2)resize(m);
                else new AlertDialog.Builder(getContext()).setTitle("버튼 삭제")
                    .setMessage("선택한 버튼을 삭제할까요?")
                    .setPositiveButton("삭제",(d,w)->{removeView(m.view);models.remove(m);save();})
                    .setNegativeButton("취소",null).show();
            }).setNegativeButton("닫기",null).show();
    }

    private void rename(Model m) {
        EditText input=new EditText(getContext());
        input.setSingleLine(true);
        input.setText(m.label);input.selectAll();
        new AlertDialog.Builder(getContext()).setTitle("버튼 이름")
            .setView(input)
            .setPositiveButton("저장",(d,w)->{
                String name=input.getText().toString().trim();
                if(!name.isEmpty()){
                    m.label=name.length()>14?name.substring(0,14):name;
                    decorate(m);save();
                }
            }).setNegativeButton("취소",null).show();
    }
    private void resize(Model m) {
        String[] sizes={"작게","보통","크게"};
        new AlertDialog.Builder(getContext()).setTitle("버튼 크기")
            .setItems(sizes,(d,w)->{
                m.width=(w==0?70:w==1?100:135);
                FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)m.view.getLayoutParams();
                p.width=dp(m.width);m.view.setLayoutParams(p);
                post(()->place(m));
                save();
            }).show();
    }
}
