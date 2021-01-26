package model;

import lombok.Builder;

@Builder(builderClassName = "Builder")
public class ItemEntity {
    private Float coreCount;
    private Integer memorySizeInMBs;
}
