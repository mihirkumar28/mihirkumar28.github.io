package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final String PREFS = "dayflow";
    private static final String KEY = "blocks";
    private final ArrayList<Block> blocks = new ArrayList<>();
    private LinearLayout timeline;
    private TextView dateView, completionView, plannedView, doneView, efficiencyView, nextLabel, nextTitle, countdown, nextMeta;
    private final Handler handler = new Handler();

    private static class Block {
        long id;
        String start, end, title, priority, status;
        Block(long id, String start, String end, String title, String priority, String status) {
            this.id=id; this.start=start; this.end=end; this.title=title; this.priority=priority; this.status=status;
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        load();
        buildUi();
        render();
        handler.postDelayed(new Runnable(){ public void run(){ render(); handler.postDelayed(this,30000); }}, 30000);
    }

    private int dp(float v){ return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private int mins(String s){ String[] p=s.split(":"); return Integer.parseInt(p[0])*60+Integer.parseInt(p[1]); }
    private String time12(String t){ int m=mins(t), h=(m/60)%12; if(h==0)h=12; return h+":"+String.format(Locale.US,"%02d",m%60)+(m>=720?" PM":" AM"); }
    private String fmt(int n){ int h=n/60,m=n%60; return h>0?h+"h "+String.format(Locale.US,"%02d",m)+"m":m+"m"; }
    private String toTime(int n){ n=Math.max(0,Math.min(1439,n)); return String.format(Locale.US,"%02d:%02d",n/60,n%60); }
    private int color(String hex){ return Color.parseColor(hex); }

    private GradientDrawable bg(String hex, float radius){ GradientDrawable g=new GradientDrawable(); g.setColor(color(hex)); g.setCornerRadius(dp(radius)); return g; }
    private TextView tv(String text, float sp, int c, boolean bold){
        TextView t=new TextView(this); t.setText(text); t.setTextSize(sp); t.setTextColor(c);
        t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL); return t;
    }
    private Button button(String text){
        Button b=new Button(this); b.setText(text); b.setTextSize(12); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setPadding(dp(10),0,dp(10),0); b.setMinHeight(0); b.setMinWidth(0); return b;
    }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout col(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(color("#0B0D12"));
        LinearLayout root=col(); root.setPadding(dp(16),dp(18),dp(16),dp(42)); scroll.addView(root);
        setContentView(scroll);

        LinearLayout top=row(); root.addView(top, new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout brandCol=col(); TextView brand=tv("DayFlow",22,Color.WHITE,true); TextView sub=tv("",12,color("#8E98AA"),false);
        dateView=sub; brandCol.addView(brand); brandCol.addView(sub);
        top.addView(brandCol,new LinearLayout.LayoutParams(0,-2,1));
        Button add=button("＋ Add block"); add.setBackground(bg("#191E2B",12)); add.setOnClickListener(v->openAddDialog());
        Button recovery=button("⚡ Recover"); recovery.setBackground(bg("#8B7CFF",12)); recovery.setOnClickListener(v->recoveryMode());
        top.addView(add,new LinearLayout.LayoutParams(dp(112),dp(42))); top.addView(recovery,new LinearLayout.LayoutParams(dp(105),dp(42)));

        LinearLayout hero=col(); hero.setPadding(dp(16),dp(16),dp(16),dp(16)); hero.setBackground(bg("#131722",18));
        LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2); heroLp.bottomMargin=dp(14); root.addView(hero,heroLp);
        TextView ey=tv("TODAY",11,color("#8E98AA"),true); hero.addView(ey);
        hero.addView(tv("Your day, under control.",27,Color.WHITE,true), new LinearLayout.LayoutParams(-1,dp(42)));
        completionView=tv("0 / 0 complete",12,color("#8E98AA"),false); hero.addView(completionView);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setTag("progress");
        progress.getProgressDrawable().setTint(color("#8B7CFF")); hero.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));
        LinearLayout metrics=row(); hero.addView(metrics,new LinearLayout.LayoutParams(-1,dp(62)));
        plannedView=metric(metrics,"0h 00m","PLANNED"); doneView=metric(metrics,"0h 00m","COMPLETED"); efficiencyView=metric(metrics,"0%","EXECUTION");

        LinearLayout side=col(); side.setPadding(dp(16),dp(16),dp(16),dp(16)); side.setBackground(bg("#131722",18));
        LinearLayout.LayoutParams sideLp=new LinearLayout.LayoutParams(-1,-2); sideLp.bottomMargin=dp(14); root.addView(side,sideLp);
        side.addView(tv("RIGHT NOW",11,color("#8E98AA"),true));
        LinearLayout nextCard=col(); nextCard.setPadding(dp(14),dp(12),dp(14),dp(12)); nextCard.setBackground(bg("#191E2B",13)); side.addView(nextCard);
        nextLabel=tv("Next block",12,color("#8E98AA"),false); nextCard.addView(nextLabel);
        nextTitle=tv("—",20,Color.WHITE,true); nextCard.addView(nextTitle);
        countdown=tv("—",30,Color.WHITE,true); nextCard.addView(countdown);
        nextMeta=tv("",12,color("#8E98AA"),false); nextCard.addView(nextMeta);

        TextView th=tv("TEMPLATES",12,color("#8E98AA"),true); side.addView(th,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout tpl=row(); side.addView(tpl);
        addTemplateButton(tpl,"WFH day","wfh"); addTemplateButton(tpl,"Office","office"); addTemplateButton(tpl,"Weekend","weekend");
        Button clear=button("Clear today's plan"); clear.setBackground(bg("#191E2B",12)); clear.setOnClickListener(v->clearDay());
        side.addView(clear,new LinearLayout.LayoutParams(-1,dp(44)));

        TextView timelineTitle=tv("TIMELINE",12,color("#8E98AA"),true); root.addView(timelineTitle,new LinearLayout.LayoutParams(-1,dp(38)));
        timeline=col(); root.addView(timeline);
        TextView foot=tv("Data is saved on this device. Closing the app does not erase your plan.",12,color("#8E98AA"),false); foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,dp(50)); flp.topMargin=dp(12); root.addView(foot,flp);
    }

    private TextView metric(LinearLayout parent,String value,String label){
        LinearLayout box=col(); box.setPadding(dp(9),dp(7),dp(9),dp(7)); box.setBackground(bg("#191E2B",12));
        TextView v=tv(value,17,Color.WHITE,true), l=tv(label,10,color("#8E98AA"),true); box.addView(v); box.addView(l);
        parent.addView(box,new LinearLayout.LayoutParams(0,dp(52),1)); return v;
    }
    private void addTemplateButton(LinearLayout p,String label,String key){
        Button b=button(label); b.setBackground(bg("#191E2B",10)); b.setOnClickListener(v->loadTemplate(key));
        p.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));
    }

    private void render(){
        Collections.sort(blocks, Comparator.comparingInt(b->mins(b.start)));
        Date now=new Date(); String ds=new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(now); dateView.setText(ds);
        int nm=Calendar.getInstance().get(Calendar.HOUR_OF_DAY)*60+Calendar.getInstance().get(Calendar.MINUTE);
        Block current=null,next=null;
        for(Block b:blocks){ if(b.status.equals("pending") && mins(b.start)<=nm && nm<mins(b.end)){current=b;break;} }
        if(current==null) for(Block b:blocks){ if(b.status.equals("pending") && mins(b.start)>nm){next=b;break;} }

        int total=blocks.size(), done=0, planned=0, doneTime=0;
        for(Block b:blocks){ int d=Math.max(0,mins(b.end)-mins(b.start)); planned+=d; if(b.status.equals("done")){done++;doneTime+=d;} }
        completionView.setText(done+" / "+total+" complete");
        plannedView.setText(fmt(planned)); doneView.setText(fmt(doneTime)); efficiencyView.setText((planned==0?0:(doneTime*100/planned))+"%");
        ProgressBar p=(ProgressBar) ((ViewGroup)completionView.getParent()).findViewWithTag("progress");
        if(p!=null) p.setProgress(total==0?0:done*100/total);

        Block target=current!=null?current:next;
        nextLabel.setText(current!=null?"Current block":"Next block");
        nextTitle.setText(target!=null?target.title:"Day complete 🎉");
        nextMeta.setText(target!=null?time12(target.start)+"–"+time12(target.end):"");
        if(target!=null){ int delta=current!=null?mins(target.end)-nm:mins(target.start)-nm; countdown.setText((current!=null?fmt(Math.max(0,delta))+" left":"in "+fmt(Math.max(0,delta)))); } else countdown.setText("—");

        timeline.removeAllViews();
        if(blocks.isEmpty()){ TextView empty=tv("No blocks yet. Add one or load a template.",14,color("#8E98AA"),false); empty.setGravity(Gravity.CENTER); timeline.addView(empty,new LinearLayout.LayoutParams(-1,dp(90))); return; }
        for(Block b:blocks) addBlockView(b,current!=null&&current.id==b.id);
    }

    private void addBlockView(Block b, boolean current){
        LinearLayout row=row(); row.setPadding(0,dp(5),0,dp(5));
        TextView time=tv(time12(b.start)+"\n"+time12(b.end),11,color("#8E98AA"),false); time.setGravity(Gravity.RIGHT|Gravity.TOP);
        row.addView(time,new LinearLayout.LayoutParams(dp(70),-1));
        LinearLayout card=col(); card.setPadding(dp(13),dp(11),dp(10),dp(10)); String bgc=b.status.equals("done")?"#131C19":b.status.equals("skipped")?"#191316":(current?"#24213E":"#151A27");
        card.setBackground(bg(bgc,12)); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1); cp.leftMargin=dp(8); row.addView(card,cp);
        TextView title=tv(b.title,15,Color.WHITE,true); card.addView(title);
        TextView meta=tv(b.priority+" · "+fmt(mins(b.end)-mins(b.start)),11,color("#8E98AA"),false); card.addView(meta);
        if(current){ TextView state=tv("● IN PROGRESS",10,color("#8B7CFF"),true); state.setPadding(0,dp(6),0,0); card.addView(state); }
        LinearLayout actions=row(); actions.setGravity(Gravity.CENTER);
        if(b.status.equals("pending")){
            Button d=button("✓"); d.setBackground(bg("#191E2B",9)); d.setOnClickListener(v->{b.status="done";save();});
            Button s=button("×"); s.setBackground(bg("#191E2B",9)); s.setOnClickListener(v->{b.status="skipped";save();});
            actions.addView(d,new LinearLayout.LayoutParams(dp(42),dp(42))); actions.addView(s,new LinearLayout.LayoutParams(dp(42),dp(42)));
        } else {
            Button u=button("Undo"); u.setBackground(bg("#191E2B",9)); u.setOnClickListener(v->{b.status="pending";save();});
            actions.addView(u,new LinearLayout.LayoutParams(dp(70),dp(42)));
        }
        row.addView(actions,new LinearLayout.LayoutParams(dp(88),-1));
        timeline.addView(row);
    }

    private void openAddDialog(){
        LinearLayout form=col(); form.setPadding(dp(18),dp(6),dp(18),dp(6));
        EditText title=new EditText(this); title.setHint("Block name"); form.addView(title);
        TimePickerDialog start=new TimePickerDialog(this,(v,h,m)->{
            String s=String.format(Locale.US,"%02d:%02d",h,m);
            TimePickerDialog endDlg=new TimePickerDialog(this,(v2,h2,m2)->{
                String e=String.format(Locale.US,"%02d:%02d",h2,m2);
                if(mins(e)<=mins(s)){Toast.makeText(this,"End time must be after start time.",Toast.LENGTH_SHORT).show();return;}
                String[] priorities={"Fixed","Important","Flexible","Optional"};
                new AlertDialog.Builder(this).setTitle("Priority").setItems(priorities,(d,which)->{
                    String p=priorities[which].toLowerCase(Locale.US); blocks.add(new Block(System.currentTimeMillis(),s,e,title.getText().toString().trim(),p,"pending")); save();
                }).show();
            },20,0,true);
            endDlg.setTitle("End time"); endDlg.show();
        },19,0,true);
        new AlertDialog.Builder(this).setTitle("Add time block").setView(form).setPositiveButton("Choose start",null).setNegativeButton("Cancel",null).create().setOnShowListener(x->{}); 
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Add time block").setView(form).setPositiveButton("Choose start",(d,w)->start.show()).setNegativeButton("Cancel",null).create();
        dlg.show();
    }

    private void recoveryMode(){
        int nm=Calendar.getInstance().get(Calendar.HOUR_OF_DAY)*60+Calendar.getInstance().get(Calendar.MINUTE); Block active=null;
        for(Block b:blocks) if(b.status.equals("pending")&&mins(b.start)<nm&&nm<mins(b.end)){active=b;break;}
        if(active==null){Toast.makeText(this,"No active block to recover.",Toast.LENGTH_SHORT).show();return;}
        int late=nm-mins(active.start), idx=blocks.indexOf(active);
        for(int i=idx+1;i<blocks.size();i++){Block b=blocks.get(i);if(!b.status.equals("pending")||b.priority.equals("fixed"))continue;int ns=mins(b.start)+late,ne=mins(b.end)+late;if(ne<1440){b.start=toTime(ns);b.end=toTime(ne);}}
        save(); Toast.makeText(this,"Recovered "+late+" minutes while protecting fixed blocks.",Toast.LENGTH_SHORT).show();
    }

    private void loadTemplate(String key){
        String[][] t;
        if(key.equals("office")) t=new String[][]{{"06:00","06:30","Wake up + routine","fixed"},{"06:30","08:00","Maths Optional","important"},{"08:00","09:00","Breakfast + commute","fixed"},{"09:00","18:30","Work + commute","fixed"},{"18:30","19:15","Reset","flexible"},{"19:15","21:15","GS study","important"},{"21:15","22:00","Dinner","fixed"},{"22:00","23:00","Interview preparation","important"},{"23:00","23:30","Wind down","flexible"}};
        else if(key.equals("weekend")) t=new String[][]{{"06:30","07:00","Morning routine","fixed"},{"07:00","10:00","GS study","important"},{"10:00","11:00","Breakfast + break","flexible"},{"11:00","14:00","Maths Optional","important"},{"14:00","15:00","Lunch","fixed"},{"15:00","17:00","Maths / revision","important"},{"17:00","18:00","Exercise","important"},{"18:00","20:00","Personal time","flexible"},{"20:00","21:00","Dinner","fixed"},{"21:00","22:30","Interview preparation","important"}};
        else t=new String[][]{{"06:00","06:20","Wake up","fixed"},{"06:20","07:00","Morning routine","flexible"},{"07:00","09:00","GS study","important"},{"09:00","09:30","Breakfast","flexible"},{"09:30","13:00","Work","fixed"},{"13:00","14:00","Lunch","fixed"},{"14:00","18:30","Work","fixed"},{"18:30","19:15","Exercise","important"},{"19:30","21:30","Maths Optional","important"},{"21:30","22:15","Dinner","fixed"},{"22:15","23:00","Interview preparation","important"},{"23:00","23:30","Wind down","flexible"}};
        blocks.clear(); for(int i=0;i<t.length;i++) blocks.add(new Block(System.currentTimeMillis()+i,t[i][0],t[i][1],t[i][2],t[i][3],"pending")); save();
    }

    private void clearDay(){ new AlertDialog.Builder(this).setTitle("Clear today's plan?").setMessage("This removes all blocks saved for today.").setPositiveButton("Clear",(d,w)->{blocks.clear();save();}).setNegativeButton("Cancel",null).show(); }

    private void save(){
        try{
            JSONArray arr=new JSONArray();
            for(Block b:blocks){JSONObject o=new JSONObject();o.put("id",b.id);o.put("start",b.start);o.put("end",b.end);o.put("title",b.title);o.put("priority",b.priority);o.put("status",b.status);arr.put(o);}
            getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,arr.toString()).apply();
        }catch(Exception ignored){}
        render();
    }
    private void load(){
        try{
            String raw=getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,null);
            if(raw!=null){JSONArray arr=new JSONArray(raw);for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);blocks.add(new Block(o.getLong("id"),o.getString("start"),o.getString("end"),o.getString("title"),o.getString("priority"),o.getString("status")));}}
            else loadTemplateSilent("wfh");
        }catch(Exception e){blocks.clear();loadTemplateSilent("wfh");}
    }
    private void loadTemplateSilent(String key){
        String[][] t=key.equals("wfh")?new String[][]{{"06:00","06:20","Wake up","fixed"},{"06:20","07:00","Morning routine","flexible"},{"07:00","09:00","GS study","important"},{"09:00","09:30","Breakfast","flexible"},{"09:30","13:00","Work","fixed"},{"13:00","14:00","Lunch","fixed"},{"14:00","18:30","Work","fixed"},{"18:30","19:15","Exercise","important"},{"19:30","21:30","Maths Optional","important"},{"21:30","22:15","Dinner","fixed"},{"22:15","23:00","Interview preparation","important"},{"23:00","23:30","Wind down","flexible"}}:new String[0][0];
        for(int i=0;i<t.length;i++)blocks.add(new Block(System.currentTimeMillis()+i,t[i][0],t[i][1],t[i][2],t[i][3],"pending"));
    }
}
