package com.zego.livedemo3;

import android.app.Application;
import android.content.Context;
import android.widget.Toast;

import com.aiyaapp.camera.sdk.AiyaEffects;
import com.aiyaapp.camera.sdk.base.ActionObserver;
import com.aiyaapp.camera.sdk.base.Event;
import com.tencent.bugly.crashreport.CrashReport;
import com.zego.livedemo3.presenters.BizLivePresenter;
import com.zego.livedemo3.utils.PreferenceUtil;


/**
 * des: 自定义Application.
 */
public class ZegoApplication extends Application{

    public static Context sApplicationContext;

    @Override
    public void onCreate() {
        super.onCreate();

        sApplicationContext = this;



        final ActionObserver observer = new ActionObserver() {
            @Override
            public void onAction(Event state) {
                if (state.eventType == Event.RESOURCE_FAILED) {
                    com.aiyaapp.camera.sdk.base.Log.e("resource failed");
                } else if (state.eventType == Event.RESOURCE_READY) {
                    com.aiyaapp.camera.sdk.base.Log.e("resource ready");
                } else if (state.eventType == Event.INIT_FAILED) {
                    com.aiyaapp.camera.sdk.base.Log.e("init failed");
                    Toast.makeText(ZegoApplication.this, "注册失败，请检查网络", Toast.LENGTH_SHORT)
                            .show();
                    AiyaEffects.getInstance().unRegisterObserver(this);
                } else if (state.eventType == Event.INIT_SUCCESS) {
                    com.aiyaapp.camera.sdk.base.Log.e("init success");
                    AiyaEffects.getInstance().unRegisterObserver(this);
                }
            }
        };
        AiyaEffects.getInstance().registerObserver(observer);
        AiyaEffects.getInstance().init(ZegoApplication.this, "");
        AiyaEffects.getInstance().setEffect("assets/modelsticker/grass/meta.json");
        BizLivePresenter.getInstance();
        ZegoApiManager.getInstance().setUseVideoFilter(true);
        ZegoApiManager.getInstance().initSDK(getResources());

        CrashReport.initCrashReport(getApplicationContext(), "e40f06d75c", false);
        // bugly初始化用户id
        CrashReport.setUserId(PreferenceUtil.getInstance().getUserID());
    }

    public Context getApplicationContext(){
        return sApplicationContext;
    }

}
