package com.tvoptimiser.home;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import java.util.*;

public class MainActivity extends Activity {
 private static final int BG=0xff101826,PANEL=0xff23334d;
 private LinearLayout root,content;
 @Override public void onCreate(Bundle b){super.onCreate(b);showHome();}
 private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(20);return d;}
 private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(5,7,5,7);return t;}
 private void page(String title,String subtitle){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(50,30,50,30);scroll.addView(root);setContentView(scroll);root.addView(text(title,32,Color.WHITE,true));root.addView(text(subtitle,19,0xffb7cce4,false));content=new LinearLayout(this);content.setOrientation(1);root.addView(content);}
 private void button(String label,String desc,Runnable action){LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(22,10,22,10);row.setBackground(shape(PANEL));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,12,0,0);content.addView(row,p);row.addView(text(label,24,Color.WHITE,true));if(desc!=null)row.addView(text(desc,17,0xffb7cce4,false));row.setFocusable(true);row.setClickable(true);row.setOnClickListener(v->action.run());row.setOnFocusChangeListener((v,f)->row.setBackground(shape(f?0xff315a85:PANEL)));}
 private void settings(String action){try{startActivity(new Intent(action));}catch(Exception e){info("Not available","Open this page from Google TV Settings instead.");}}
 private void info(String title,String msg){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK",null).show();}
 private void showHome(){page("TV OPTIMISER","A clean, simple home screen for Google TV 4K");button("MY APPS","Open installed apps",this::showApps);button("SAFE DEBLOAT","Review and uninstall apps individually",this::showDebloat);button("PERFORMANCE","Settings to make menus feel faster",this::showPerformance);button("STORAGE","Free up space safely",this::showStorage);button("SYSTEM SETTINGS","Open Google TV Settings",()->settings(Settings.ACTION_SETTINGS));button("HOME SCREEN HELP","Information about replacing Google TV Home",this::showLauncherHelp);}
 private static class TvApp{String name,pkg;TvApp(String n,String p){name=n;pkg=p;}}
 private List<TvApp> apps(){List<TvApp> out=new ArrayList<>();Set<String> seen=new HashSet<>();PackageManager pm=getPackageManager();for(String cat:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(cat);for(ResolveInfo r:pm.queryIntentActivities(q,0)){if(r.activityInfo==null)continue;String pkg=r.activityInfo.packageName;if(pkg.equals(getPackageName())||!seen.add(pkg))continue;out.add(new TvApp(String.valueOf(r.loadLabel(pm)),pkg));}}Collections.sort(out,(a,b)->a.name.compareToIgnoreCase(b.name));return out;}
 private void showApps(){page("MY APPS","Select an app to open");button("BACK","Main menu",this::showHome);for(TvApp a:apps())button(a.name,"Open",()->{PackageManager pm=getPackageManager();Intent i=pm.getLeanbackLaunchIntentForPackage(a.pkg);if(i==null)i=pm.getLaunchIntentForPackage(a.pkg);if(i!=null){try{startActivity(i);}catch(Exception e){info("Cannot launch","Try opening the app from Google TV.");}}else info("Cannot launch","This app has no accessible launch screen.");});}
 private void showDebloat(){page("SAFE DEBLOAT","No apps are deleted automatically.");button("BACK","Main menu",this::showHome);button("ALL APP SETTINGS","Show installed and system apps",()->settings(Settings.ACTION_APPLICATION_SETTINGS));button("IMPORTANT SAFETY INFORMATION","Do not disable essential Google TV components",()->info("Leave these enabled","Never disable Google Play services, Android System, Settings, Package Installer or the active home launcher. This tool does not remove protected system apps."));for(TvApp a:apps())button(a.name,"Open its Android app settings",()->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.pkg)));}catch(Exception e){settings(Settings.ACTION_APPLICATION_SETTINGS);}});}
 private void showPerformance(){page("PERFORMANCE","Animations affect apparent menu speed, not hardware speed.");button("BACK","Main menu",this::showHome);button("DEVELOPER OPTIONS","Open performance-related settings",()->settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));button("USE 0.5x ANIMATIONS","Window, transition and animator scales",()->info("Animation settings","In Developer options, set Window animation scale, Transition animation scale and Animator duration scale to 0.5x. Return to 1x to undo."));button("RESTART HELP","Settings → System → Restart",()->info("Restart","Restart from Google TV Settings → System → Restart."));}
 private void showStorage(){page("STORAGE","Clear cache, not data, to keep app sign-ins.");button("BACK","Main menu",this::showHome);button("STORAGE SETTINGS","View free space",()->settings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));button("APP CACHE SETTINGS","Choose app → Storage & cache → Clear cache",()->settings(Settings.ACTION_APPLICATION_SETTINGS));button("CACHE VS DATA","Prevent accidental loss of logins",()->info("Be careful","Clear cache removes temporary files. Clear storage/data may erase sign-ins, downloads and app settings."));}
 private void showLauncherHelp(){page("HOME SCREEN","Google TV may block third-party default launchers.");button("BACK","Main menu",this::showHome);button("HOME APP SETTINGS","Open chooser if supported",()->settings(Settings.ACTION_HOME_SETTINGS));button("IMPORTANT","Replacing Google Home may need a separate reversible ADB setup",()->info("Launcher restriction","This app offers a clean launcher screen but may not become the default when pressing Home. Never disable the built-in launcher without a recovery method."));}
 @Override public void onBackPressed(){showHome();}
}