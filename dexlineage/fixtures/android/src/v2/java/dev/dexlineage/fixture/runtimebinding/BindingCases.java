package dev.dexlineage.fixture.runtimebinding;

public final class BindingCases {
    private static volatile Descriptor unrelated;
    private static volatile Descriptor cached;
    public static Descriptor lookup() {
        Descriptor result = cached;
        if (result == null) {
            synchronized (BindingCases.class) {
                result = cached;
                if (result == null) {
                    Builder b = Descriptor.builder();
                    b.response = CodecFactory.wrap(OutputMessage.DEFAULT);
                    b.fullName = Descriptor.name("fixture.runtime.Service", "Lookup");
                    b.request = CodecFactory.wrap(InputMessage.DEFAULT);
                    result = b.build();
                    cached = result;
                }
            }
        }
        return result;
    }
    public static Descriptor wrongReturn() {
        Builder b = Descriptor.builder();
        b.response = CodecFactory.wrap(OutputMessage.DEFAULT);
        b.fullName = Descriptor.name("fixture.runtime.Service", "WrongReturn");
        b.request = CodecFactory.wrap(InputMessage.DEFAULT);
        cached = b.build();
        return unrelated;
    }
}
final class Descriptor {
    final WireCodec response;
    final WireCodec request;
    final String fullName;
    Descriptor(String name, WireCodec response, WireCodec request) {
        this.response = response; this.request = request; this.fullName = name;
    }
    static Builder builder() { return new Builder(); }
    static String name(String service, String method) { return service + "/" + method; }
    public String toString() {
        Labels text = new Labels();
        text.add("responseMarshaller", response);
        text.add("fullMethodName", fullName);
        text.add("requestMarshaller", request);
        return text.toString();
    }
}
final class Builder {
    WireCodec response;
    WireCodec request;
    String fullName;
    Descriptor build() { return new Descriptor(fullName, response, request); }
}
