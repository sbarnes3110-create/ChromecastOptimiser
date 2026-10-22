package org.fitclear.hm08

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Accessible read-only Health Connect view; never writes or exports records. */
class HealthActivity : ComponentActivity() {
    private val needed = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )
    private var client:HealthConnectClient?=null
    private lateinit var state:TextView
    private lateinit var steps:TextView
    private lateinit var sleep:TextView
    private lateinit var heart:TextView
    private val permissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if(granted.containsAll(needed)) refresh()
        else state.text="Access not granted. Tap ALLOW READ ACCESS to try again."
    }
    private fun px(n:Int)= (resources.displayMetrics.density*n+0.5f).toInt()
    private fun label(s:String,size:Int,bold:Boolean=false)=TextView(this).apply{
        text=s
        setTextColor(Color.WHITE)
        textSize=size.toFloat()
        setTypeface(Typeface.DEFAULT, if(bold)Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(px(2).toFloat(),1.1f)
    }
    private fun add(parent:LinearLayout,view:View,top:Int){
        parent.addView(view,LinearLayout.LayoutParams(-1,-2).apply{topMargin=px(top)})
    }
    private fun button(parent:LinearLayout,s:String,action:()->Unit){
        val b=Button(this).apply{
            text=s
            isAllCaps=false
            textSize=21f
            setTypeface(Typeface.DEFAULT,Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(px(12),px(14),px(12),px(14))
            minHeight=px(75)
            background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=px(12).toFloat()}
            setOnClickListener{action()}
        }
        add(parent,b,14)
    }
    private fun stat(parent:LinearLayout,name:String):TextView{
        val tile=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(px(16),px(20),px(16),px(20))
            background=GradientDrawable().apply{
                setColor(Color.BLACK);setStroke(px(2),Color.WHITE);cornerRadius=px(12).toFloat()
            }
        }
        tile.addView(label(name,21))
        val v=label("—",34,true)
        add(tile,v,8)
        add(parent,tile,14)
        return v
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.BLACK
        window.navigationBarColor=Color.BLACK
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.BLACK)}
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(px(20),px(28),px(20),px(40))
        }
        scroll.addView(root)
        setContentView(scroll)
        add(root,label("FITCLEAR",36,true),0)
        add(root,label("HEALTH CONNECT",26,true),10)
        add(root,label("LARGE PRINT • READ ONLY",17),10)
        steps=stat(root,"TODAY'S STEPS")
        sleep=stat(root,"LATEST SLEEP")
        heart=stat(root,"LATEST HEART RATE")
        state=label("Checking Health Connect…",21)
        add(root,state,20)
        button(root,"ALLOW READ ACCESS"){requestPermissionsSafely()}
        button(root,"REFRESH MY READINGS"){refresh()}
        button(root,"OPEN HEALTH CONNECT"){
            try{startActivity(Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS"))}
            catch(e:Exception){state.text="Open Android Settings and search for Health Connect."}
        }
        add(root,label("These readings come from Health Connect. They might come from other apps, not necessarily your HM08. FitClear does not upload, change or delete health data.",19),18)
        add(root,label("If no readings appear, FitCloudPro may not yet be sharing with Health Connect. Keep your watch paired to FitCloudPro.",19),14)
        initialise()
    }
    private fun initialise(){
        try{
            when(HealthConnectClient.getSdkStatus(this)){
                HealthConnectClient.SDK_AVAILABLE -> {
                    client=HealthConnectClient.getOrCreate(this)
                    state.text="Health Connect is available. Tap ALLOW READ ACCESS if asked."
                    refresh()
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    state.text="Health Connect needs an update or installation from Google Play."
                else ->state.text="Health Connect is not available on this phone."
            }
        }catch(e:Exception){
            state.text="Setup error: "+e.javaClass.simpleName
        }
    }
    private fun requestPermissionsSafely(){
        val hc=client?:run{state.text="Health Connect unavailable.";return}
        lifecycleScope.launch{
            try{
                val granted=hc.permissionController.getGrantedPermissions()
                if(granted.containsAll(needed))refresh() else permissionsLauncher.launch(needed)
            }catch(e:Exception){state.text="Permission error: "+e.javaClass.simpleName}
        }
    }
    private fun refresh(){
        val hc=client?:return
        lifecycleScope.launch{
            try{
                if(!hc.permissionController.getGrantedPermissions().containsAll(needed)){
                    state.text="Tap ALLOW READ ACCESS first. No readings have been accessed."
                    return@launch
                }
                state.text="Reading Health Connect…"
                val now=Instant.now()
                val dayStart=ZonedDateTime.now().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val aggregate=hc.aggregate(AggregateRequest(
                    metrics=setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter=TimeRangeFilter.between(dayStart,now)
                ))
                val count=aggregate[StepsRecord.COUNT_TOTAL]?:0L
                steps.text=if(count>0)String.format("%,d",count) else "No steps"
                val sleepRecords=hc.readRecords(ReadRecordsRequest(
                    recordType=SleepSessionRecord::class,
                    timeRangeFilter=TimeRangeFilter.between(now.minus(72,ChronoUnit.HOURS),now),
                    pageSize=100
                ))
                val lastSleep=sleepRecords.records.maxByOrNull{it.endTime}
                if(lastSleep==null) sleep.text="No sleep data" else{
                    val minutes=Duration.between(lastSleep.startTime,lastSleep.endTime).toMinutes().coerceAtLeast(0)
                    sleep.text=(minutes/60).toString()+"h "+(minutes%60).toString()+"m"
                }
                val rates=hc.readRecords(ReadRecordsRequest(
                    recordType=HeartRateRecord::class,
                    timeRangeFilter=TimeRangeFilter.between(now.minus(48,ChronoUnit.HOURS),now),
                    pageSize=500
                ))
                val latest=rates.records.flatMap{it.samples}.maxByOrNull{it.time}
                heart.text=if(latest==null)"No heart data" else latest.beatsPerMinute.toString()+" bpm"
                state.text="Reading complete. Data source may include apps other than FitCloudPro."
            }catch(e:SecurityException){
                state.text="Health access denied. Check your permissions."
            }catch(e:Exception){
                state.text="Reading error: "+e.javaClass.simpleName
            }
        }
    }
}
