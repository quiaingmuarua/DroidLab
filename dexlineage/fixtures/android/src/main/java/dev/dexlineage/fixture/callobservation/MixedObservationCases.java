package dev.dexlineage.fixture.callobservation;

import java.io.InputStream;

/** Both branches are complete, but they disagree about the marshaller role. */
final class MixedObservationCases {
    private static volatile MixedDescriptor cached;
    static MixedDescriptor lookup() {
        MixedDescriptor result = cached;
        if (result == null) {
            MixedBuilder builder = MixedDescriptor.builder();
            builder.fullName = MixedDescriptor.name("fixture.observation.Service", "MixedStream");
            builder.request = new Codec(InputMessage.DEFAULT);
            builder.response = new Codec(OutputMessage.DEFAULT);
            result = builder.build();
            cached = result;
        }
        return result;
    }
}

final class MixedDescriptor {
    final String fullName;
    final WireCodec request;
    final WireCodec response;
    MixedDescriptor(String fullName, WireCodec request, WireCodec response) {
        this.fullName = fullName;
        this.request = request;
        this.response = response;
    }
    static MixedBuilder builder() { return new MixedBuilder(); }
    static String name(String service, String method) { return service + "/" + method; }
    InputStream open(Object message) {
        if (message == null) return response.encode(message);
        return request.encode(message);
    }
    public String toString() {
        Labels labels = new Labels();
        labels.add("fullMethodName", fullName);
        labels.add("requestMarshaller", request);
        labels.add("responseMarshaller", response);
        return labels.toString();
    }
}

final class MixedBuilder {
    String fullName;
    WireCodec request;
    WireCodec response;
    MixedDescriptor build() { return new MixedDescriptor(fullName, request, response); }
}

final class MixedRawCall extends AbstractCall {
    private final MixedDescriptor descriptor;
    private final Transport transport = new Transport();
    MixedRawCall(MixedDescriptor descriptor) { this.descriptor = descriptor; }
    String marker() { return "fixture.observation.mixed-stream-call"; }
    public void start(ConcreteListener listener, Metadata headers) { descriptor.fullName.length(); }
    public void send(Object message) { transport.write(descriptor.open(message)); }
    static void finish(ConcreteListener listener, Status status, Metadata trailers) {
        listener.close(status, trailers);
    }
}
