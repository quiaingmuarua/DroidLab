package dev.dexlineage.fixture.runtimebinding;

public final class BindingCases {
    private static volatile Descriptor cached;
    private static volatile Descriptor unrelated;
    public static Descriptor lookup() {
        Descriptor result = cached;
        if (result == null) {
            synchronized (BindingCases.class) {
                result = cached;
                if (result == null) {
                    Builder b = Descriptor.builder();
                    b.fullName = Descriptor.name("fixture.runtime.Service", "Lookup");
                    b.request = new Codec(InputMessage.DEFAULT);
                    b.response = new Codec(OutputMessage.DEFAULT);
                    result = b.build();
                    cached = result;
                }
            }
        }
        return result;
    }
    public static Descriptor wrongReturn() {
        Builder b = Descriptor.builder();
        b.fullName = Descriptor.name("fixture.runtime.Service", "WrongReturn");
        b.request = new Codec(InputMessage.DEFAULT);
        b.response = new Codec(OutputMessage.DEFAULT);
        cached = b.build();
        return unrelated;
    }
}
final class Descriptor {
    final String fullName;
    final WireCodec request;
    final WireCodec response;
    Descriptor(WireCodec request, String name, WireCodec response) {
        this.request = request; this.fullName = name; this.response = response;
    }
    static Builder builder() { return new Builder(); }
    static String name(String service, String method) { return service + "/" + method; }
    public String toString() {
        Labels text = new Labels();
        text.add("fullMethodName", fullName);
        text.add("requestMarshaller", request);
        text.add("responseMarshaller", response);
        return text.toString();
    }
}
final class Builder {
    WireCodec request;
    String fullName;
    WireCodec response;
    Descriptor build() { return new Descriptor(request, fullName, response); }
}
