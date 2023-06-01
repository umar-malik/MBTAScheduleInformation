package com.knimbus.mbtascheduleinformation.controllers;

import com.knimbus.mbtascheduleinformation.models.TrainInfoModel;
import com.knimbus.mbtascheduleinformation.services.PredictionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MBTARunner implements CommandLineRunner {

    private final PredictionService predictionService;

    public MBTARunner(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Override
    public void run(String... args) throws Exception {
        List<Map.Entry<String, ArrayList<TrainInfoModel>>> predictions = predictionService.getPredictions();
        predictionService.displayData(predictions);
    }
}
