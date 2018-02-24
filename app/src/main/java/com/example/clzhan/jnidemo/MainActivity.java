package com.example.clzhan.jnidemo;

import android.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
private TextView tv;

    private MyAudioRecorder recorder = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv=(TextView)findViewById(R.id.textView2);
       // new AlertDialog.Builder( this ).setMessage( GetString() ).show();

    }

//
//    static
//    {
//        System.loadLibrary("hello-jni");
//    }
//    public static native String GetString(); // 本地库函数
//
//    public static native int AppStartConnect(); // 开始测试
//
//    public static native  int AppStopConnect(); // 停止测试



    public void onBtn(View view){
        tv.setText("111111");

        recorder = new MyAudioRecorder();

        recorder.InitRecording();

        recorder.startRecording();

        //AppStartConnect();
    }
    public void onBtn1(View view){
        tv.setText("2222222");
        //AppStopConnect();
       recorder.stopRecording();

        if (recorder != null) {
            recorder = null;
        }

    }
}
