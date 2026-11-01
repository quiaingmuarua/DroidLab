package com.app.androidlab.sample.proto;

import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

public class ThriftDEMO {


    public void demo() throws TException {
        User u = new User();
        u.setId(1112);
        u.setName("AndroidFriend");
        u.setAge(20);
        TSerializer serializer = new TSerializer(new TCompactProtocol.Factory());
        byte[] bytes = serializer.serialize( u);

        TDeserializer deserializer = new TDeserializer(new TCompactProtocol.Factory());
        User u2 = new User();
        deserializer.deserialize(u2, bytes);

    }


    public static void demo1(String[] args) throws Exception {
        TTransport transport =
                new TSocket("localhost", 9090);

        transport.open();

        TProtocol protocol = new TBinaryProtocol(transport);
        HelloService.Client client =
                new HelloService.Client(protocol);

        System.out.println(client.getUser(1));
        System.out.println(client.ping());

        transport.close();
    }
}
