# Only these self-authored library API member names are an explicit contract.
# All role classes, fields, call overrides, callbacks and RPC members still rename.
-keepclassmembers class dev.dexlineage.fixture.callobservation.Status {
    public dev.dexlineage.fixture.callobservation.StatusCode getCode();
    public java.lang.String getDescription();
}
-keepclassmembers class dev.dexlineage.fixture.callobservation.StatusCode {
    public int value();
}
-keepclassmembers interface dev.dexlineage.fixture.callobservation.MessageApi {
    public int getSerializedSize();
    public byte[] toByteArray();
}
-keepclassmembers class dev.dexlineage.fixture.callobservation.BytesBase {
    public byte[] toByteArray();
}
-keepclassmembers class dev.dexlineage.fixture.callobservation.MessageBase {
    public int getSerializedSize();
}
