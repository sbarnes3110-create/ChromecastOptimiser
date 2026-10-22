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
    private val sourceLabels=mutableMapOf<String,TextView>()
    private val permissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if(granted.containsAll(needed)) {
            state.text="Permission granted. Reading Health Connect…"
            refresh()
        } else {
            state.text="No complete access yet. Open Health Connect below to check FitClear's permissions, or tap ALLOW READ ACCESS again."
        }
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
        val source=label("Source not yet checked",17)
        add(tile,source,8)
        sourceLabels[name]=source
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
        sleep=stat(root,"LONGEST SLEEP (3 DAYS)")
        heart=stat(root,"LATEST HEART RATE")
        state=label("Checking Health Connect…",21)
        add(root,state,20)
        button(root,"ALLOW READ ACCESS"){requestPermissionsSafely()}
        button(root,"REFRESH MY READINGS"){refresh()}
        button(root,"OPEN HEALTH CONNECT"){
            state.text="Opening Health Connect settings…"
            try {
                startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            } catch(e:Exception) {
                state.text="Couldn't open Health Connect automatically. Search for Health Connect in Android Settings."
            }
        }
        add(root,label("Sources are reported by Health Connect. Steps can come from your phone, watch companion or other fitness apps. Longest sleep is a session, not necessarily the entire night's sleep. Nothing is uploaded or changed by FitClear.",19),18)
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
        state.text="Permission button pressed. Checking access…"
        val hc=client?:run{state.text="Health Connect unavailable. Try OPEN HEALTH CONNECT below.";return}
        lifecycleScope.launch {
            try {
                val granted=hc.permissionController.getGrantedPermissions()
                if(granted.containsAll(needed)) {
                    state.text="Already permitted. Reading your health records…"
                    refresh()
                } else {
                    state.text="Opening Android's Health Connect permission screen…"
                    permissionsLauncher.launch(needed)
                }
            } catch(e:Exception) {
                state.text="Unable to open permissions: "+e.javaClass.simpleName+
                  ". Try OPEN HEALTH CONNECT below to grant access manually."
            }
        }
    }
    private fun sourceName(packageName:String):String {
        val p=packageName.lowercase()
        return when {
            p=="android" || p.startsWith("com.android.healthconnect.phone.") ->
                "Your phone's step counter"
            "fitcloud" in p -> "FitCloudPro ("+packageName+")"
            "myfitnesspal" in p -> "MyFitnessPal ("+packageName+")"
            "google" in p && "fitness" in p -> "Google Fit ("+packageName+")"
            else -> packageName
        }
    }
    private fun sources(packages:Iterable<String>):String {
        val names=packages.distinct().map{sourceName(it)}
        return if(names.isEmpty())"No contributing source reported"
               else names.take(4).joinToString("; ") +
                    (if(names.size>4)"; and "+(names.size-4)+" more" else "")
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
                sourceLabels["TODAY'S STEPS"]?.text="FROM: "+sources(aggregate.dataOrigins.map{it.packageName})
                val sleepRecords=hc.readRecords(ReadRecordsRequest(
                    recordType=SleepSessionRecord::class,
                    timeRangeFilter=TimeRangeFilter.between(now.minus(72,ChronoUnit.HOURS),now),
                    pageSize=100
                ))
                // A brief 3-minute fragment must not appear as a full night.
                val longest=sleepRecords.records.maxByOrNull {
                    Duration.between(it.startTime,it.endTime).toMinutes()
                }
                if(longest==null){
                    sleep.text="No sleep records"
                    sourceLabels["LONGEST SLEEP (3 DAYS)"]?.text="No source reported"
                }else{
                    val mins=Duration.between(longest.startTime,longest.endTime).toMinutes().coerceAtLeast(0)
                    if(mins<30){
                        sleep.text="Brief only: "+mins+"m"
                        sourceLabels["LONGEST SLEEP (3 DAYS)"]?.text =
                            "Short fragment; not a full night's sleep. FROM: "+
                                sourceName(longest.metadata.dataOrigin.packageName)
                    }else{
                        sleep.text=(mins/60).toString()+"h "+(mins%60).toString()+"m"
                        sourceLabels["LONGEST SLEEP (3 DAYS)"]?.text =
                            "Longest recorded session, not necessarily total sleep. FROM: "+
                                sourceName(longest.metadata.dataOrigin.packageName)
                    }
                }
                val rates=hc.readRecords(ReadRecordsRequest(
                    recordType=HeartRateRecord::class,
                    timeRangeFilter=TimeRangeFilter.between(now.minus(48,ChronoUnit.HOURS),now),
                    pageSize=500
                ))
                val latestPair=rates.records.flatMap{record ->
                    record.samples.map{sample -> Pair(record.metadata.dataOrigin.packageName,sample)}
                }.maxByOrNull{it.second.time}
                heart.text=if(latestPair==null)"No heart data"
                    else latestPair.second.beatsPerMinute.toString()+" bpm"
                sourceLabels["LATEST HEART RATE"]?.text=
                    if(latestPair==null)"No source reported"
                    else "FROM: "+sourceName(latestPair.first)
                state.text="Reading complete. Sources shown below each measurement."
            }catch(e:SecurityException){
                state.text="Health access denied. Check your permissions."
            }catch(e:Exception){
                state.text="Reading error: "+e.javaClass.simpleName
            }
        }
    }
}
