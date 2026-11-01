package com.app.androidlab.sample.proto;

import org.apache.thrift.TException;

public class HelloServiceImpl implements HelloService.Iface {

    @Override
    public User getUser(long id) {
        User user = new User();
        user.setId(id);
        user.setName("Tom");
        return user;
    }

    @Override
    public String ping() throws TException {
        return "";
    }



    @Override
    public void saveUser(User user) throws TException {

    }
}
