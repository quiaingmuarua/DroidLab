package com.app.androidlab.sample.network.rpc;




import com.app.androidlab.sample.proto.ExampleServiceGrpc;
import com.app.androidlab.sample.proto.Request;
import com.app.androidlab.sample.proto.Response;

import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GrpcClientDemo {
    private final ManagedChannel channel;
    private final ExampleServiceGrpc.ExampleServiceBlockingStub blockingStub;
    private final ExampleServiceGrpc.ExampleServiceStub asyncStub;

    public GrpcClientDemo(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build());
    }

    GrpcClientDemo(ManagedChannel channel) {
        this.channel = channel;

        blockingStub = ExampleServiceGrpc.newBlockingStub(channel);
        asyncStub = ExampleServiceGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void unaryCall(String message) {
        Request request = Request.newBuilder().setData(message).build();
        Response response;
        try {
            response = blockingStub.unaryCall(request);
            System.out.println("Unary call response: " + response.getResult());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void serverStreamingCall(String message) {
        Request request = Request.newBuilder().setData(message).build();
        try {
            blockingStub.serverStreamingCall(request).forEachRemaining(response -> {
                System.out.println("Server streaming response: " + response.getResult());
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clientStreamingCall(String[] messages) throws InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<Response> responseObserver = new StreamObserver<Response>() {
            @Override
            public void onNext(Response response) {
                System.out.println("Client streaming response: " + response.getResult());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        };

        StreamObserver<Request> requestObserver = asyncStub.clientStreamingCall(responseObserver);
        try {
            for (String message : messages) {
                Request request = Request.newBuilder().setData(message).build();
                requestObserver.onNext(request);
            }
        } catch (RuntimeException e) {
            requestObserver.onError(e);
            throw e;
        }
        requestObserver.onCompleted();
        finishLatch.await(1, TimeUnit.MINUTES);
    }

    public void bidirectionalStreamingCall(String[] messages) throws InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<Request> requestObserver = asyncStub.bidirectionalStreamingCall(new StreamObserver<Response>() {
            @Override
            public void onNext(Response response) {
                System.out.println("Bidirectional streaming response: " + response.getResult());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        });

        try {
            for (String message : messages) {
                Request request = Request.newBuilder().setData(message).build();
                requestObserver.onNext(request);
            }
        } catch (RuntimeException e) {
            requestObserver.onError(e);
            throw e;
        }
        requestObserver.onCompleted();
        finishLatch.await(1, TimeUnit.MINUTES);
    }

    public static void main(String[] args) throws InterruptedException {
        GrpcClientDemo client = new GrpcClientDemo("localhost", 50051);

        client.unaryCall("Hello, unary call!");
        client.serverStreamingCall("Hello, server streaming!");
        client.clientStreamingCall(new String[]{"Message 1", "Message 2", "Message 3"});
        client.bidirectionalStreamingCall(new String[]{"Message A", "Message B", "Message C"});

        client.shutdown();



    }
    
    
    public static void CronetClient(Context context){

    }
}
