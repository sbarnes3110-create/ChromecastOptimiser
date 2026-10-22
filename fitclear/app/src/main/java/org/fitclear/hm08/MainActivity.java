package org.fitclear.hm08;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/** FitClear HM08 diagnostic: read-only GATT, no commands, unpairing or data writes. */
public class MainActivity extends Activity {
    private static final int REQ_BLUETOOTH=100;
    private static final UUID VENDOR=UUID.fromString("000001ff-3c17-d293-8e48-14fe2e4da212");
    private static final UUID FF03=UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb");
    private static final UUID FF04=UUID.fromString("0000ff04-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY=UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID LEVEL=UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private TextView title, detail;
    private Button readButton;
    private BluetoothDevice watch;
    private BluetoothGatt gatt;
    private boolean connected=false, discovered=false;
    private final ArrayList<BluetoothGattCharacteristic> readCandidates=new ArrayList<>();
    private final ArrayList<String> readOutcomes=new ArrayList<>();
    private int nextReadIndex=0;
    private boolean readInProgress=false;
    private BluetoothGattCharacteristic currentReading;
    private int px(int dp){return (int)(getResources().getDisplayMetrics().density*dp+0.5f);}
    private TextView label(String value,int size,boolean bold){
        TextView t=new TextView(this); t.setText(value); t.setTextColor(Color.WHITE);
        t.setTextSize(size); t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);
        t.setLineSpacing(px(2),1.1f); return t;
    }
    private GradientDrawable outline(boolean filled){
        GradientDrawable d=new GradientDrawable();
        d.setColor(filled?Color.WHITE:Color.BLACK);
        d.setStroke(px(2),Color.WHITE); d.setCornerRadius(px(12)); return d;
    }
    private void add(LinearLayout parent,View view,int top){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=px(top); parent.addView(view,lp);
    }
    private Button make(String value, boolean filled, Runnable action){
        Button b=new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(21);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(filled?Color.BLACK:Color.WHITE); b.setBackground(outline(filled));
        b.setMinHeight(px(72)); b.setPadding(px(12),px(13),px(12),px(13));
        b.setOnClickListener(v->action.run()); return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.BLACK);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(20),px(30),px(20),px(45)); scroll.addView(root); setContentView(scroll);
        add(root,label("FITCLEAR",36,true),0);
        add(root,label("HM08-98F safe Bluetooth test",24,true),8);
        add(root,label("READ-ONLY • NO WATCH SETTINGS CHANGED",17,false),16);
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(16),px(20),px(16),px(20)); panel.setBackground(outline(false));
        title=label("READY",30,true); detail=label("Find the HM08 watch already paired with Android.",22,false);
        panel.addView(title); add(panel,detail,14); add(root,panel,26);
        add(root,make("1. FIND MY WATCH",true,this::findWatch),22);
        add(root,make("2. CONNECT READ-ONLY",false,this::connect),13);
        readButton=make("3. CHECK READ CHANNELS",false,this::readStatus);
        readButton.setEnabled(false); readButton.setAlpha(.5f); add(root,readButton,13);
        add(root,make("COPY TEST RESULT",false,this::copy),13);
        add(root,make("DISCONNECT",false,this::disconnect),13);
        add(root,label("This is a connection test, NOT a complete replacement for FitCloudPro. It cannot sync steps, sleep, messages or MyFitnessPal yet. FitCloudPro may disconnect briefly during the test. Never unpair or reset your watch.",20,false),25);
    }
    private void status(String heading,String text){
        runOnUiThread(()->{title.setText(heading);detail.setText(text);});
    }
    private void canRead(boolean yes){
        runOnUiThread(()->{readButton.setEnabled(yes);readButton.setAlpha(yes?1f:.5f);});
    }
    private boolean permission(){
        if(Build.VERSION.SDK_INT<31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BLUETOOTH);
        status("ALLOW ACCESS","Allow Nearby devices, then tap FIND MY WATCH again.");
        return false;
    }
    @Override public void onRequestPermissionsResult(int r,String[] permissions,int[] results){
        super.onRequestPermissionsResult(r,permissions,results);
        if(r==REQ_BLUETOOTH && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) findWatch();
    }
    private void findWatch(){
        if(!permission())return;
        try{
            BluetoothManager bm=(BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=bm==null?null:bm.getAdapter();
            if(adapter==null || !adapter.isEnabled()){status("BLUETOOTH OFF","Turn on Bluetooth and try again.");return;}
            watch=null;
            Set<BluetoothDevice> paired=adapter.getBondedDevices();
            for(BluetoothDevice item:paired){
                String name=item.getName();
                if(name!=null && name.toUpperCase(Locale.ROOT).startsWith("HM08")){
                    watch=item; if(name.equalsIgnoreCase("HM08-98F")) break;
                }
            }
            if(watch==null)status("NOT FOUND","No paired HM08 watch in Android. Do not unpair FitCloudPro.");
            else status("WATCH FOUND",watch.getName()+" is paired. Tap CONNECT READ-ONLY.");
        }catch(Exception ex){status("SCAN ERROR",ex.getClass().getSimpleName()+". Nothing changed.");}
    }
    private void connect(){
        if(!permission())return;
        if(watch==null){status("FIND FIRST","Tap FIND MY WATCH.");return;}
        closeGatt();
        status("CONNECTING","Opening a temporary Bluetooth link. No watch commands will be sent.");
        try{
            gatt=watch.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);
            if(gatt==null)status("NOT CONNECTED","Android refused to create a connection.");
        }catch(Exception ex){status("CONNECT ERROR",ex.getClass().getSimpleName());}
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt current,int result,int state){
            if(current!=gatt)return;
            if(result==BluetoothGatt.GATT_SUCCESS && state==BluetoothGatt.STATE_CONNECTED){
                connected=true; status("CONNECTED","Discovering readable Bluetooth services.");
                try{if(!current.discoverServices())status("SERVICE ERROR","Discovery failed to start.");}
                catch(Exception ex){status("SERVICE ERROR",ex.getClass().getSimpleName());}
            }else if(state==BluetoothGatt.STATE_DISCONNECTED || result!=BluetoothGatt.GATT_SUCCESS){
                connected=false;discovered=false;readInProgress=false;canRead(false);
                status("DISCONNECTED","Bluetooth code "+result+". This does not prove incompatibility.");
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt current,int result){
            if(current!=gatt)return;
            if(result!=BluetoothGatt.GATT_SUCCESS){status("SERVICE ERROR","Discovery code "+result);return;}
            discovered=true;
            boolean hasVendor=current.getService(VENDOR)!=null;
            boolean hasBattery=current.getService(BATTERY)!=null;
            canRead(hasVendor||hasBattery);
            status("CONNECTED — TEST PASSED","Services found: "+current.getServices().size()
                +"\nHM08 000001FF service: "+(hasVendor?"YES":"NO")
                +"\nStandard battery service: "+(hasBattery?"YES":"NO")
                +"\n\nThis proves the Android app reached your watch. It does not prove full FitCloudPro or MyFitnessPal support.");
        }
        @Override @SuppressWarnings("deprecation") public void onCharacteristicRead(BluetoothGatt current,BluetoothGattCharacteristic c,int code){
            display(current,c.getUuid(),c.getValue(),code);
        }
        @Override public void onCharacteristicRead(BluetoothGatt current,BluetoothGattCharacteristic c,byte[] value,int code){
            display(current,c.getUuid(),value,code);
        }
    };
    private void includeCandidate(BluetoothGattService service, UUID id){
        if(service==null)return;
        BluetoothGattCharacteristic c=service.getCharacteristic(id);
        if(c!=null && (c.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0)readCandidates.add(c);
    }
    private void readStatus(){
        if(!permission() || gatt==null || !connected || !discovered){status("NOT READY","Connect first.");return;}
        if(readInProgress){status("READING","The safe reading test is already in progress.");return;}
        readCandidates.clear();readOutcomes.clear();
        includeCandidate(gatt.getService(BATTERY),LEVEL);
        BluetoothGattService service=gatt.getService(VENDOR);
        // Never send vendor commands, write values, configure notification descriptors,
        // unpair or modify any watch settings.
        includeCandidate(service,FF04);
        includeCandidate(service,FF03);
        if(readCandidates.isEmpty()){status("NO READ CHANNELS","The HM08 exposes no supported readable channels.");return;}
        nextReadIndex=0;readInProgress=true;canRead(false);
        status("CHECKING","Testing "+readCandidates.size()+" read-only channels.");
        readNext();
    }
    private String shortId(UUID id){
        if(id.equals(LEVEL))return "Battery";
        String u=id.toString();
        if(u.startsWith("0000ff04"))return "FF04";
        if(u.startsWith("0000ff03"))return "FF03";
        return u;
    }
    private void readNext(){
        if(!readInProgress || gatt==null || !connected)return;
        if(nextReadIndex>=readCandidates.size()){
            readInProgress=false;currentReading=null;canRead(true);
            status("READ TEST COMPLETE",android.text.TextUtils.join("\n",readOutcomes)
              +"\n\nNo watch settings were changed. These are diagnostics, not decoded health data.");
            return;
        }
        currentReading=readCandidates.get(nextReadIndex++);
        try{
            if(!gatt.readCharacteristic(currentReading)){
                readOutcomes.add(shortId(currentReading.getUuid())+": Android could not start the read.");
                readNext();
            }
        }catch(Exception ex){
            readOutcomes.add(shortId(currentReading.getUuid())+": "+ex.getClass().getSimpleName());
            readNext();
        }
    }
    private void display(BluetoothGatt current,UUID uuid,byte[] bytes,int code){
        if(current!=gatt || !readInProgress || currentReading==null || !uuid.equals(currentReading.getUuid()))return;
        String channel=shortId(uuid);
        if(code==2){
            readOutcomes.add(channel+": Read not permitted (code 2).");
        }else if(code!=BluetoothGatt.GATT_SUCCESS){
            readOutcomes.add(channel+": Read failed (code "+code+").");
        }else if(uuid.equals(LEVEL)&&bytes!=null&&bytes.length>0){
            readOutcomes.add("Battery: "+(bytes[0]&255)+"% (standard reading).");
        }else{
            StringBuilder hex=new StringBuilder();
            if(bytes!=null)for(int i=0;i<Math.min(bytes.length,32);i++){
                if(i>0)hex.append(" ");
                hex.append(String.format(Locale.ROOT,"%02X",bytes[i]&255));
            }
            readOutcomes.add(channel+": Read successful, raw bytes "+(hex.length()>0?hex:"(empty)")+".");
        }
        readNext();
    }
    private void copy(){
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm!=null){cm.setPrimaryClip(ClipData.newPlainText("FitClear status",title.getText()+"\n"+detail.getText()));
            status("COPIED","Paste the Bluetooth result into ChatGPT.\n\n"+detail.getText());
        }
    }
    private void closeGatt(){
        connected=false;discovered=false;canRead(false);
        BluetoothGatt old=gatt;gatt=null;
        if(old!=null){try{old.disconnect();}catch(Exception ignored){}try{old.close();}catch(Exception ignored){}}
    }
    private void disconnect(){closeGatt();status("DISCONNECTED SAFELY","Reopen FitCloudPro if necessary. Do not unpair or reset the watch.");}
    @Override protected void onDestroy(){closeGatt();super.onDestroy();}
}
