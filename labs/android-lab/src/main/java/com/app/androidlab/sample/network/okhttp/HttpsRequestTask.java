package com.app.androidlab.sample.network.okhttp;

import android.os.AsyncTask;

public  class HttpsRequestTask extends AsyncTask<String, Void, AsyncTaskResult> {
    private AsyncResponse delegate = null;

    public HttpsRequestTask(AsyncResponse delegate) {
        this.delegate = delegate;
    }

    @Override
    protected AsyncTaskResult doInBackground(String... urls) {
        String url = urls[0];
        String result = OkhttpUtils.makeHttpsRequest(url);
        return new AsyncTaskResult(result, url,urls[1]);
    }

    @Override
    protected void onPostExecute(AsyncTaskResult result) {
        if (delegate != null) {
            delegate.processFinish(result);
        }
    }
}