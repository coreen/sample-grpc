package model;

import com.google.protobuf.ByteString;
import lombok.Builder;

@Builder(builderClassName = "Builder")
public class KeyEntity {
    private static final String KEY_FORMAT = "%s/%s";

    private String groupId;
    private String itemId;

    public ByteString getByteString() {
        final String composite = String.format(KEY_FORMAT, groupId, itemId);
        return ByteString.copyFrom(composite.getBytes());
    }
}
