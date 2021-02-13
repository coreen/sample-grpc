package com.example.model;

import com.google.protobuf.ByteString;
import lombok.Builder;

@Builder(builderClassName = "Builder")
public class KeyEntity {
    // 2 types of items are possible:
    // * items belonging to a particular group, used to list items by group
    // * details about the group itself
    private static final String ITEM_KEY_FORMAT = "groupId/%s/itemId/%s";
    private static final String GROUP_KEY_FORMAT = "groupId/%s/details";

    private String groupId;
    private String itemId;

    public ByteString getItemByteString() {
        final String composite = String.format(ITEM_KEY_FORMAT, groupId, itemId);
        return ByteString.copyFrom(composite.getBytes());
    }

    public ByteString getGroupByteString() {
        final String composite = String.format(GROUP_KEY_FORMAT, groupId);
        return ByteString.copyFrom(composite.getBytes());
    }
}
