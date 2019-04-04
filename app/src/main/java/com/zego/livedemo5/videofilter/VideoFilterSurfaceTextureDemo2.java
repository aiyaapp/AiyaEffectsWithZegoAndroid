package com.zego.livedemo5.videofilter;

import android.app.Application;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.aiyaapp.aiya.AYEffectHandler;
import com.aiyaapp.aiya.gpuImage.AYGPUImageConstants;
import com.zego.livedemo5.ZegoApplication;
import com.zego.livedemo5.videocapture.ve_gl.EglBase;
import com.zego.livedemo5.videocapture.ve_gl.EglBase14;
import com.zego.livedemo5.videocapture.ve_gl.GlRectDrawer;
import com.zego.livedemo5.videocapture.ve_gl.GlUtil;
import com.zego.zegoliveroom.videofilter.ZegoVideoFilter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import static android.opengl.GLES20.*;
import static com.zego.livedemo5.videocapture.ve_gl.GlRectDrawer.OES_FRAGMENT_SHADER_STRING;
import static com.zego.livedemo5.videocapture.ve_gl.GlRectDrawer.RGB_FRAGMENT_SHADER_STRING;

/**
 * Created by robotding on 17/3/28.
 */

public class VideoFilterSurfaceTextureDemo2 extends ZegoVideoFilter implements SurfaceTexture.OnFrameAvailableListener {
    private ZegoVideoFilter.Client mClient = null;
    private HandlerThread mThread = null;
    private volatile Handler mHandler = null;

    private EglBase mDummyContext = null;
    private EglBase mEglContext = null;
    private int mInputWidth = 0;
    private int mInputHeight = 0;
    private int mOutputWidth = 0;
    private int mOutputHeight = 0;
    private SurfaceTexture mInputSurfaceTexture = null;
    private int mInputTextureId = 0;
    private Surface mOutputSurface = null;
    private boolean mIsEgl14 = false;

    private GlRectDrawer mDrawer = null;
    private float[] transformationMatrix = new float[]{1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };

    // ---------- 哎吖科技添加开始 ----------
    AYEffectHandler effectHandler;

    private int[] framebuffer = new int[1];
    public int[] texture = new int[1];

    private int[] bindingFrameBuffer = new int[1];
    private int[] bindingRenderBuffer = new int[1];
    private int[] viewPoint = new int[4];
    private int vertexAttribEnableArraySize = 5;
    private ArrayList<Integer> vertexAttribEnableArray = new ArrayList(vertexAttribEnableArraySize);
    // ---------- 哎吖科技添加结束 ----------

    @Override
    protected void allocateAndStart(ZegoVideoFilter.Client client) {
        mClient = client;
        mThread = new HandlerThread("video-filter");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());

        mInputWidth = 0;
        mInputHeight = 0;

        final CountDownLatch barrier = new CountDownLatch(1);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mDummyContext = EglBase.create(null, EglBase.CONFIG_PIXEL_BUFFER);

                try {
                    mDummyContext.createDummyPbufferSurface();
                    mDummyContext.makeCurrent();
                } catch (RuntimeException e) {
                    // Clean up before rethrowing the exception.
                    mDummyContext.releaseSurface();
                    throw e;
                }

                mInputTextureId = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                mInputSurfaceTexture = new SurfaceTexture(mInputTextureId);
                mInputSurfaceTexture.setOnFrameAvailableListener(VideoFilterSurfaceTextureDemo2.this);

                mEglContext = EglBase.create(mDummyContext.getEglBaseContext(), EglBase.CONFIG_RECORDABLE);
                mIsEgl14 = EglBase14.isEGL14Supported();

                if (mDrawer == null) {
                    mDrawer = new GlRectDrawer();
                }

                barrier.countDown();
            }
        });
        try {
            barrier.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void stopAndDeAllocate() {
        final CountDownLatch barrier = new CountDownLatch(1);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                release();
                barrier.countDown();
            }
        });

        try {
            barrier.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mHandler = null;

        mThread.quit();
        mThread = null;

        mClient.destroy();
        mClient = null;
    }

    @Override
    protected int supportBufferType() {
        return BUFFER_TYPE_SURFACE_TEXTURE;
    }

    @Override
    protected int dequeueInputBuffer(final int width, final int height, int stride) {
        if (stride != width * 4) {
            return -1;
        }

        if (mInputWidth != width || mInputHeight != height) {
            if (mClient.dequeueInputBuffer(width, height, stride) < 0) {
                return -1;
            }

            final SurfaceTexture surfaceTexture = mClient.getSurfaceTexture();
            final CountDownLatch barrier = new CountDownLatch(1);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setOutputSurface(surfaceTexture, width, height);
                    barrier.countDown();
                }
            });
            try {
                barrier.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return 0;
    }

    @Override
    protected ByteBuffer getInputBuffer(int index) {
        return null;
    }

    @Override
    protected void queueInputBuffer(int bufferIndex, int width, int height, int stride, long timestamp_100n) {
    }

    @Override
    protected SurfaceTexture getSurfaceTexture() {
        return mInputSurfaceTexture;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mDummyContext.makeCurrent();
        surfaceTexture.updateTexImage();
        long timestampNs = surfaceTexture.getTimestamp();

        mEglContext.makeCurrent();

        // ---------- 哎吖科技添加开始 ----------
        // 保存opengl状态
        saveOpenGLState();

        // 初始化program
        mDrawer.prepareShader(RGB_FRAGMENT_SHADER_STRING, transformationMatrix);
        mDrawer.prepareShader(OES_FRAGMENT_SHADER_STRING, transformationMatrix);

        // 使用program
        mDrawer.userProgram(OES_FRAGMENT_SHADER_STRING, transformationMatrix);

        // 生成framebuffer
        if (framebuffer[0] == 0) {
            generateFramebuffer();
        }

        // 使用framebuffer
        activateFramebuffer();

        // ---------- 哎吖科技添加结束 ----------

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        mDrawer.drawOes(mInputTextureId, transformationMatrix,
                        mOutputWidth, mOutputHeight, 0, 0, mOutputWidth, mOutputHeight);

        // ---------- 哎吖科技添加开始 ----------
        // 绘制特效
        if (effectHandler == null) {
            effectHandler = new AYEffectHandler(ZegoApplication.sApplicationContext);
            // 设置美颜程度
            effectHandler.setIntensityOfBeauty(0.5f);
            // 设置大眼瘦脸
            effectHandler.setIntensityOfBigEye(0.2f);
            effectHandler.setIntensityOfSlimFace(0.8f);

            try {
                // 添加滤镜
                effectHandler.setStyle(BitmapFactory.decodeStream(ZegoApplication.sApplicationContext.getAssets().open("FilterResources/filter/03桃花.JPG")));
                effectHandler.setIntensityOfStyle(0.5f);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 设置正常的方向
            effectHandler.setRotateMode(AYGPUImageConstants.AYGPUImageRotationMode.kAYGPUImageFlipHorizontal);

            // 设置特效
            effectHandler.setEffectPath(ZegoApplication.sApplicationContext.getExternalCacheDir() + "/aiya/effect/2017/meta.json");
        }

        effectHandler.processWithTexture(texture[0], mOutputWidth, mOutputHeight);

        // 使用program
        mDrawer.userProgram(RGB_FRAGMENT_SHADER_STRING, transformationMatrix);

        // 恢复opengl状态
        restoreOpenGLState();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // 绘制到默认的framebuffer上
        mDrawer.drawRgb(texture[0], transformationMatrix,
                mOutputWidth, mOutputHeight, 0, 0, mOutputWidth, mOutputHeight);
        // ---------- 哎吖科技添加结束 ----------

        if (mIsEgl14) {
            ((EglBase14) mEglContext).swapBuffers(timestampNs);
        } else {
            mEglContext.swapBuffers();
        }
    }

    private void setOutputSurface(SurfaceTexture surfaceTexture, int width, int height) {
        if (mOutputSurface != null) {
            mEglContext.releaseSurface();
            mOutputSurface.release();
            mOutputSurface = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            surfaceTexture.setDefaultBufferSize(width, height);
        }
        mOutputSurface = new Surface(surfaceTexture);
        mOutputWidth = width;
        mOutputHeight = height;

        mEglContext.createSurface(mOutputSurface);
    }

    private void release() {
        mInputSurfaceTexture.release();
        mInputSurfaceTexture = null;

        mDummyContext.makeCurrent();
        if (mInputTextureId != 0) {
            int[] textures = new int[] {mInputTextureId};
            GLES20.glDeleteTextures(1, textures, 0);
            mInputTextureId = 0;
        }
        mDummyContext.release();
        mDummyContext = null;

        if (mDrawer != null) {
            mDrawer.release();
            mDrawer = null;
        }

        // ---------- 哎吖科技添加开始 ----------
        destroyFramebuffer();
        effectHandler.destroy();
        // ---------- 哎吖科技添加结束 ----------

        mEglContext.release();
        mEglContext = null;
        if (mOutputSurface != null) {
            mOutputSurface.release();
            mOutputSurface = null;
        }
    }

    // ---------- 哎吖科技添加开始 ----------
    private void generateFramebuffer() {
        generateFramebuffer(mOutputWidth, mOutputHeight, GL_LINEAR, GL_LINEAR, GL_CLAMP_TO_EDGE, GL_CLAMP_TO_EDGE, GL_RGBA, GL_RGBA, GL_UNSIGNED_BYTE);
    }
    private void generateFramebuffer(int width, int height, int minFilter, int magFilter, int wrapS, int wrapT, int internalFormat, int format, int type) {
        glGenFramebuffers(1, framebuffer, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer[0]);

        glActiveTexture(GL_TEXTURE1);
        glGenTextures(1, texture, 0);
        glBindTexture(GL_TEXTURE_2D, texture[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT);

        glBindTexture(GL_TEXTURE_2D, texture[0]);

        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture[0], 0);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void destroyFramebuffer() {

        if (framebuffer[0] != 0){
            glDeleteFramebuffers(1, framebuffer, 0);
            framebuffer[0] = 0;
        }
        if (texture[0] != 0) {
            glDeleteTextures(1, texture, 0);
            texture[0] = 0;
        }
    }

    public void activateFramebuffer() {
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer[0]);
        glViewport(0, 0, mOutputWidth, mOutputHeight);
    }

    private void saveOpenGLState() {
        // 获取当前绑定的FrameBuffer
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, bindingFrameBuffer, 0);

        // 获取当前绑定的RenderBuffer
        glGetIntegerv(GL_RENDERBUFFER_BINDING, bindingRenderBuffer, 0);

        // 获取viewpoint
        glGetIntegerv(GL_VIEWPORT, viewPoint, 0);

        // 获取顶点数据
        vertexAttribEnableArray.clear();
        for (int x = 0 ; x < vertexAttribEnableArraySize; x++) {
            int[] vertexAttribEnable = new int[1];
            glGetVertexAttribiv(x, GL_VERTEX_ATTRIB_ARRAY_ENABLED, vertexAttribEnable, 0);
            if (vertexAttribEnable[0] != 0) {
                vertexAttribEnableArray.add(x);
            }
        }
    }

    private void restoreOpenGLState() {
        // 还原当前绑定的FrameBuffer
        glBindFramebuffer(GL_FRAMEBUFFER, bindingFrameBuffer[0]);

        // 还原当前绑定的RenderBuffer
        glBindRenderbuffer(GL_RENDERBUFFER, bindingRenderBuffer[0]);

        // 还原viewpoint
        glViewport(viewPoint[0], viewPoint[1], viewPoint[2], viewPoint[3]);

        // 还原顶点数据
        for (int x = 0 ; x < vertexAttribEnableArray.size(); x++) {
            glEnableVertexAttribArray(vertexAttribEnableArray.get(x));
        }
    }
    // ---------- 哎吖科技添加结束 ----------
}
