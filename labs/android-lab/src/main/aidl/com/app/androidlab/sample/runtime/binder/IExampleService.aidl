// IExampleService.aidl
package com.app.androidlab.sample.runtime.binder;
import com.app.androidlab.sample.runtime.binder.IExampleCallback;

interface IExampleService {
    void registerCallback(IExampleCallback callback);
    void doSomething();
}


