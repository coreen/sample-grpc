package model;

import lombok.Builder;

import java.util.List;

@Builder(builderClassName = "Builder")
public class GroupEntity {
    private List<String> networks;
    private List<String> volumes;
}
