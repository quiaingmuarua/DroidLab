package dev.dexlineage.fixture.runtimebinding;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

// Self-authored local binding shapes; no RPC or Android execution is needed.
interface WireCodec {
    Object decode(InputStream input);
    InputStream encode(Object value);
}
final class InputMessage { static final InputMessage DEFAULT = new InputMessage(); }
final class OutputMessage { static final OutputMessage DEFAULT = new OutputMessage(); }
final class Codec implements WireCodec {
    final Object prototype;
    Codec(Object prototype) { this.prototype = prototype; }
    public Object decode(InputStream input) { return prototype; }
    public InputStream encode(Object value) { return new ByteArrayInputStream(new byte[0]); }
}
final class CodecFactory {
    static WireCodec wrap(Object prototype) { return new Codec(prototype); }
}
final class Labels {
    private final StringBuilder text = new StringBuilder();
    Labels add(String name, Object value) { text.append(name).append('=').append(value); return this; }
    public String toString() { return text.toString(); }
}
