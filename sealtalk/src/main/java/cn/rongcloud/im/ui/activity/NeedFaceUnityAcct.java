package cn.rongcloud.im.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.faceunity.nama.FURenderer;

import cn.rongcloud.im.R;
import io.rong.callkit.util.PreferenceUtil;


public class NeedFaceUnityAcct extends AppCompatActivity {

    private boolean isOn = true;//是否使用FaceUnity

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_faceunity);

        final Button button = (Button) findViewById(R.id.btn_set);
        String isOpen = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_IS_ON);
        if (TextUtils.isEmpty(isOpen) || isOpen.equals("false")) {
            isOn = false;
        } else {
            isOn = true;
        }
        button.setText(isOn ? "On" : "Off");

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isOn = !isOn;
                button.setText(isOn ? "On" : "Off");
            }
        });

        Button btn_to_main = (Button) findViewById(R.id.btn_to_main);
        btn_to_main.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(NeedFaceUnityAcct.this, SplashActivity.class);
                PreferenceUtil.persistString(NeedFaceUnityAcct.this, PreferenceUtil.KEY_FACEUNITY_IS_ON,
                        isOn + "");
                new Thread(() -> {
                    if (isOn) {
                        FURenderer.getInstance().setup(NeedFaceUnityAcct.this);
                    }
                }).start();
                startActivity(intent);
                finish();
            }
        });

    }
}
