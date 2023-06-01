package com.knimbus.mbtascheduleinformation.models;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainInfoModel {
    private String route;
    private String destinationName;
    private long directionId;
    private long minutesToDeparture;

    @Override
    public String toString() {
        return destinationName + ": Departing in "
                + (minutesToDeparture == 0 ? "less than 1 minute" : "" + minutesToDeparture + " minutes");

    }
}
