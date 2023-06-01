package com.knimbus.mbtascheduleinformation.services;

import com.knimbus.mbtascheduleinformation.models.TrainInfoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface PredictionService {
    /**
     * This method calls the MBTA API to retrieve the train predictions and process them to generate the data in a
     * specific format
     *
     * @return A HashMap containing the Route's Long Name as the key and list of trains departing from that line
     */
    List<Map.Entry<String, ArrayList<TrainInfoModel>>> getPredictions();

    /**
     * This displays the train data in a specific format
     *
     * @param trainInfos
     */
    void displayData(List<Map.Entry<String, ArrayList<TrainInfoModel>>> trainInfos);
}
