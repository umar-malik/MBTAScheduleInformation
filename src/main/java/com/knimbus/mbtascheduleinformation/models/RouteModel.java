package com.knimbus.mbtascheduleinformation.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteModel {
    private String id;
    private String destination_0;
    private String destination_1;
    private String longName;
}
