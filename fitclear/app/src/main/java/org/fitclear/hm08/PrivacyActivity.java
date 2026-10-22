package org.fitclear.hm08;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shows a local, transparent explanation of requested Health Connect permissions. */
public final class PrivacyActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        ScrollView root=new ScrollView(this);
        root.setBackgroundColor(Color.BLACK);
        TextView explanation=new TextView(this);
        explanation.setText("FITCLEAR — HEALTH DATA PRIVACY\n\n"+
            "FitClear requests permission to READ steps, sleep sessions and heart-rate records in Health Connect.\n\n"+
            "We use these records only to display your own readings in large print on your phone.\n\n"+
            "FitClear does not upload, sell, modify, delete or share these records. "+
            "No account is required. No watch data is written. "+
            "You can deny or revoke permission in Health Connect at any time.\n\n"+
            "These readings may have been provided by apps other than FitCloudPro.");
        explanation.setTextColor(Color.WHITE);
        explanation.setTextSize(23);
        explanation.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        int p=(int)(20*getResources().getDisplayMetrics().density);
        explanation.setPadding(p,p,p,p);
        root.addView(explanation);
        setContentView(root);
    }
}
