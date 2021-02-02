package model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(builderClassName = "Builder")
public class ItemEntity {
    private String itemId;
    private Float coreCount;
    private Integer memorySizeInMBs;
}
