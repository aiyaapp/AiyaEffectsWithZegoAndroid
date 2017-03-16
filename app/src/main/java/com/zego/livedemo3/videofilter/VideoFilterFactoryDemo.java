package com.zego.livedemo3.videofilter;

import android.content.res.Resources;

import com.zego.livedemo3.aiya.AiyaFilterDemo;
import com.zego.zegoavkit2.videofilter.ZegoVideoFilter;
import com.zego.zegoavkit2.videofilter.ZegoVideoFilterFactory;

/**
 * Created by robotding on 16/12/3.
 */

public class VideoFilterFactoryDemo extends ZegoVideoFilterFactory {
    private int mode = 1;
    private ZegoVideoFilter mFilter = null;
    private Resources res;

    public VideoFilterFactoryDemo(Resources res){
        this.res=res;
    }

    public ZegoVideoFilter create() {
//        switch (mode) {
//            case 0:
//                mFilter = new VideoFilterMemDemo();
//                break;
//            case 1:
//                mFilter = new VideoFilterSurfaceTextureDemo();
//                break;
//            case 2:
//                mFilter = new VideoFilterHybridDemo(res);
//                break;
//        }
        mFilter = new AiyaFilterDemo(res);

        return mFilter;
    }

    public void destroy(ZegoVideoFilter vf) {
        mFilter = null;
    }
}
