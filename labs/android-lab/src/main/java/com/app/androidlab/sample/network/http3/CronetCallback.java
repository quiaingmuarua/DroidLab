package com.app.androidlab.sample.network.http3;

public interface CronetCallback {
    /**
     * 当 Cronet 请求完成时调用
     * @param result 包含请求结果的对象
     */
    void onCronetFinish(CronetResult result);
}
