package dev.dexlineage.fixture.callobservation;

public final class ObservationCases {
    private static volatile Descriptor cached;
    public static Descriptor lookup() {
        Descriptor result = cached;
        if (result == null) {
            synchronized (ObservationCases.class) {
                result = cached;
                if (result == null) {
                    Builder builder = Descriptor.builder();
                    builder.fullName = Descriptor.name("fixture.observation.Service", "Lookup");
                    builder.request = new Codec(InputMessage.DEFAULT);
                    builder.response = new Codec(OutputMessage.DEFAULT);
                    result = builder.build();
                    cached = result;
                }
            }
        }
        return result;
    }
}

final class Descriptor {
    final String diagnosticLabel;
    final String fullName;
    final WireCodec request;
    final WireCodec response;
    Descriptor(WireCodec request, String name, WireCodec response) {
        this.diagnosticLabel = "unrelated descriptor label";
        this.request = request;
        this.fullName = name;
        this.response = response;
    }
    static Builder builder() { return new Builder(); }
    static String name(String service, String method) { return service + "/" + method; }
    java.io.InputStream open(Object message) { return request.encode(message); }
    public String toString() {
        Labels labels = new Labels();
        labels.add("diagnosticLabel", diagnosticLabel);
        labels.add("fullMethodName", fullName);
        labels.add("requestMarshaller", request);
        labels.add("responseMarshaller", response);
        return labels.toString();
    }
}

final class Builder {
    WireCodec request;
    String fullName;
    WireCodec response;
    Descriptor build() { return new Descriptor(request, fullName, response); }
}

final class RawCall extends AbstractCall {
    private final Descriptor descriptor;
    private ConcreteListener listener;
    private Metadata headers;
    private final Transport transport = new Transport();
    RawCall(Descriptor descriptor) { this.descriptor = descriptor; }
    String marker() { return "fixture.observation.raw-call"; }
    static void finish(ConcreteListener listener, Status status, Metadata trailers) {
        listener.close(status, trailers);
    }
    public void start(ConcreteListener listener, Metadata headers) {
        descriptor.fullName.length();
        this.listener = listener;
        this.headers = headers;
    }
    public void send(Object message) { transport.write(descriptor.open(message)); }
    // Same signature as send, but not an inherited call-contract override.
    private void sendInternal(Object message) { transport.write(descriptor.open(message)); }
    private void startInternal(ConcreteListener listener, Metadata headers) {
        this.listener = listener;
        this.headers = headers;
    }
}

abstract class AmbiguousContract extends AbstractCall {
    public abstract void alternateSend(Object message);
}

final class AmbiguousRawCall extends AmbiguousContract {
    private final Descriptor descriptor;
    private ConcreteListener listener;
    private final Transport transport = new Transport();
    AmbiguousRawCall(Descriptor descriptor) { this.descriptor = descriptor; }
    String marker() { return "fixture.observation.ambiguous-call"; }
    static void finish(ConcreteListener listener, Status status, Metadata trailers) {
        listener.close(status, trailers);
    }
    public void start(ConcreteListener listener, Metadata headers) { descriptor.fullName.length(); this.listener = listener; }
    public void send(Object message) { transport.write(descriptor.open(message)); }
    public void alternateSend(Object message) { transport.write(descriptor.open(message)); }
}

final class UnknownRawCall extends AbstractCall {
    private final Descriptor descriptor;
    private final Transport transport = new Transport();
    UnknownRawCall(Descriptor descriptor) { this.descriptor = descriptor; }
    String marker() { return "fixture.observation.unknown-call"; }
    static void finish(ConcreteListener listener, Status status, Metadata trailers) {
        listener.close(status, trailers);
    }
    public void start(ConcreteListener listener, Metadata headers) { descriptor.fullName.length(); }
    public void send(Object message) { first(message); }
    private void first(Object message) { second(message); }
    private void second(Object message) { transport.write(descriptor.open(message)); }
}

final class UncertainCompetingCall extends AmbiguousContract {
    private final Descriptor descriptor;
    private final Transport transport = new Transport();
    UncertainCompetingCall(Descriptor descriptor) { this.descriptor = descriptor; }
    String marker() { return "fixture.observation.uncertain-competing-call"; }
    static void finish(ConcreteListener listener, Status status, Metadata trailers) {
        listener.close(status, trailers);
    }
    public void start(ConcreteListener listener, Metadata headers) { descriptor.fullName.length(); }
    public void send(Object message) { transport.write(descriptor.open(message)); }
    public void alternateSend(Object message) { first(message); }
    private void first(Object message) { second(message); }
    private void second(Object message) { transport.write(descriptor.open(message)); }
}
