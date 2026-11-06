package dev.dexlineage.fixture.callobservation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** Self-authored local call shapes. This fixture never opens a network connection. */
public final class ObservationSupport {
    private ObservationSupport() {}
}

final class Metadata {
    String marker() { return "fixture.observation.metadata"; }
}

abstract class AbstractCall {
    public abstract void start(ConcreteListener listener, Metadata metadata);
    public abstract void send(Object message);
}

abstract class ListenerBase {
    public abstract void response(Object message);
    public abstract void close(Status status, Metadata trailers);
}

class RecordingListener extends ListenerBase {
    Object lastMessage;
    Status lastStatus;
    Metadata lastTrailers;
    public void response(Object message) { lastMessage = message; }
    public void close(Status status, Metadata trailers) {
        lastStatus = status;
        lastTrailers = trailers;
    }
}

// The start parameter does not declare either callback: they are inherited.
final class ConcreteListener extends RecordingListener {}

final class Transport {
    private InputStream sent;
    void write(InputStream input) { sent = input; }
}

final class Status {
    private final StatusCode code;
    private final String description;
    Status(StatusCode code, String description) { this.code = code; this.description = description; }
    public StatusCode getCode() { return code; }
    public String getDescription() { return description; }
}

final class StatusCode {
    private final int number;
    StatusCode(int number) { this.number = number; }
    public int value() { return number; }
}

interface MessageApi {
    int getSerializedSize();
    byte[] toByteArray();
}

abstract class BytesBase implements MessageApi {
    public byte[] toByteArray() { return new byte[] { 8, 1 }; }
}

abstract class MessageBase extends BytesBase {
    public int getSerializedSize() { return 2; }
}

final class InputMessage extends MessageBase { static final InputMessage DEFAULT = new InputMessage(); }
final class OutputMessage extends MessageBase { static final OutputMessage DEFAULT = new OutputMessage(); }

interface WireCodec {
    Object decode(InputStream input);
    InputStream encode(Object value);
}

final class Codec implements WireCodec {
    final Object prototype;
    Codec(Object prototype) { this.prototype = prototype; }
    public Object decode(InputStream input) { return prototype; }
    public InputStream encode(Object value) { return new ByteArrayInputStream(((MessageApi) value).toByteArray()); }
}

final class CodecFactory {
    static WireCodec wrap(Object prototype) { return new Codec(prototype); }
}

final class Labels {
    private final StringBuilder text = new StringBuilder();
    Labels add(String name, Object value) { text.append(name).append('=').append(value); return this; }
    public String toString() { return text.toString(); }
}
