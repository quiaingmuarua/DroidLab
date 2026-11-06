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
                    builder.response = CodecFactory.wrap(OutputMessage.DEFAULT);
                    builder.fullName = Descriptor.name("fixture.observation.Service", "Lookup");
                    builder.request = CodecFactory.wrap(InputMessage.DEFAULT);
                    result = builder.build();
                    cached = result;
                }
            }
        }
        return result;
    }
}

final class Descriptor {
    final WireCodec response;
    final String fullName;
    final WireCodec request;
    final String diagnosticLabel;
    Descriptor(String name, WireCodec response, WireCodec request) {
        this.response = response;
        this.fullName = name;
        this.request = request;
        this.diagnosticLabel = "unrelated descriptor label";
    }
    static Builder builder() { return new Builder(); }
    static String name(String service, String method) { return service + "/" + method; }
    java.io.InputStream open(Object message) { return request.encode(message); }
    public String toString() {
        Labels labels = new Labels();
        labels.add("responseMarshaller", response);
        labels.add("diagnosticLabel", diagnosticLabel);
        labels.add("requestMarshaller", request);
        labels.add("fullMethodName", fullName);
        return labels.toString();
    }
}

final class Builder {
    WireCodec response;
    WireCodec request;
    String fullName;
    Descriptor build() { return new Descriptor(fullName, response, request); }
}

final class RawCall extends AbstractCall {
    private Metadata headers;
    private ConcreteListener listener;
    private final Descriptor descriptor;
    private final Transport transport = new Transport();
    RawCall(Metadata headers, Descriptor descriptor) { this.headers = headers; this.descriptor = descriptor; }
    String marker() { return "fixture.observation.raw-call"; }
    static void finish(ConcreteListener listener, Status status, Metadata trailers) {
        listener.close(status, trailers);
    }
    public void start(ConcreteListener listener, Metadata headers) { descriptor.fullName.length(); startInternal(listener, headers); }
    public void send(Object message) { sendInternal(message); }
    private void sendInternal(Object message) { transport.write(descriptor.open(message)); }
    private void startInternal(ConcreteListener listener, Metadata headers) {
        this.headers = headers;
        this.listener = listener;
    }
}

abstract class AmbiguousContract extends AbstractCall {
    public abstract void alternateSend(Object message);
}

final class AmbiguousRawCall extends AmbiguousContract {
    private ConcreteListener listener;
    private final Descriptor descriptor;
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
