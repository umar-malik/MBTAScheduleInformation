package com.knimbus.mbtascheduleinformation.services;

import com.knimbus.mbtascheduleinformation.models.RouteModel;
import com.knimbus.mbtascheduleinformation.models.TrainInfoModel;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Primary
public class PredictionServiceImpl implements PredictionService {
    final private String BASE_API = "https://api-v3.mbta.com/predictions/";

    @Override
    public List<Map.Entry<String, ArrayList<TrainInfoModel>>> getPredictions() {
        List<Map.Entry<String, ArrayList<TrainInfoModel>>> trainData = null;
        String responseStr = "";

        URI uri = URI.create(BASE_API + "?filter[stop]=place-pktrm&sort=departure_time&include=route");

        // create HttpRequest object using builder patter (newBuilder method of HttpRequest)
        final HttpRequest request = HttpRequest.newBuilder()
                // set the URI
                .uri(uri)
                // Set the Http protocol to http/1.1
                .version(HttpClient.Version.HTTP_1_1)
                // set the timeout for the request to be of 60 seconds
                .timeout(Duration.ofSeconds(60))
                // set the method
                .GET()
                // build the object
                .build();

        HttpClient client = HttpClient.newBuilder().build();

        try {

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            responseStr = response.body();

            trainData = processPredictionData(responseStr);
        } catch (IOException | InterruptedException e) {
            System.out.println("Error completing the request: " + e.getMessage());
        }

        return trainData;
    }

    @Override
    public void displayData(List<Map.Entry<String, ArrayList<TrainInfoModel>>> trainInfos) {
        trainInfos.forEach(e -> {
            System.out.println("----" + e.getKey() + "----");
            e.getValue().forEach(System.out::println);

            System.out.println();
        });
    }

    /**
     * This method processes the complete response received from the API endpoint invocation.
     *
     * @param responseStr
     * @return
     */
    private List<Map.Entry<String, ArrayList<TrainInfoModel>>> processPredictionData(String responseStr) {
        JSONObject jsonObj = convertToJson(responseStr);
        JSONArray dataArray = (JSONArray) jsonObj.get("data");
        JSONArray includedArray = (JSONArray) jsonObj.get("included");

        HashMap<String, RouteModel> routesMap = new HashMap<>();

        includedArray.forEach(r -> {
            // convert each to JSONObject to respective RouteInfo model
            RouteModel currentModel = getRouteModel((JSONObject) r);
            routesMap.put(currentModel.getId(), currentModel);
        });

        ZonedDateTime now = ZonedDateTime.now(ZoneId.ofOffset("UTC", ZoneOffset.of("-04:00")));
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
        System.out.println("Current time: " + dateTimeFormatter.format(now));

        ArrayList<TrainInfoModel> trainInfos = new ArrayList<>();

        for (int arrayIndex = 0; arrayIndex < dataArray.size(); arrayIndex++) {
            // convert each JSONObject to proper TrainInfo model
            TrainInfoModel trainInfo = getTrainInfo(dataArray, now, arrayIndex, routesMap);

            // add the current train info to list only of there is still time for departure
            if (trainInfo != null && trainInfo.getMinutesToDeparture() >= 0) trainInfos.add(trainInfo);
            // if the list size is 10, then break the loop as we do not need to display more than 10 trains
            if (trainInfos.size() == 10) break;
        }

        // group and sort the data based on the line and departure
        HashMap<String, ArrayList<TrainInfoModel>> groupedTrainInfos = new HashMap<>();

        createMapForRouteAndTrainInfo(routesMap, trainInfos, groupedTrainInfos);

        List<Map.Entry<String, ArrayList<TrainInfoModel>>> sortedEntries = sortMap(groupedTrainInfos);

        return sortedEntries;
    }

    /**
     * This method process the passed HashMap and returns a list of Sorted Entries based on the Comparator defined at
     * the code
     *
     * @param groupedTrainInfos
     * @return
     */
    private List<Map.Entry<String, ArrayList<TrainInfoModel>>> sortMap(HashMap<String, ArrayList<TrainInfoModel>> groupedTrainInfos) {
        List<Map.Entry<String, ArrayList<TrainInfoModel>>> entryList = new ArrayList<>(groupedTrainInfos.entrySet());
        Collections.sort(entryList, k);

        return entryList;
    }

    /**
     * This method creates a HashMap object from the passed trainInfos and routesMap objects. The HashMap is generated
     * based on the Long Route name as the key
     *
     * @param routesMap
     * @param trainInfos
     * @param groupedTrainInfos
     */
    private void createMapForRouteAndTrainInfo(HashMap<String, RouteModel> routesMap,
                                               ArrayList<TrainInfoModel> trainInfos,
                                               HashMap<String, ArrayList<TrainInfoModel>> groupedTrainInfos) {
        trainInfos.forEach(t -> {
            // fetch the full line name for current train
            RouteModel currentRoute = routesMap.get(t.getRoute());

            // create a new ArrayList and add it for the given key if it is not already associated with a value
            ArrayList<TrainInfoModel> currentObject = groupedTrainInfos.putIfAbsent(currentRoute.getLongName(),
                    new ArrayList<>(List.of(t)));

            // check if the key was already associated to a value.
            if (currentObject != null) {
                // add the current Train Info to the existing list
                groupedTrainInfos.get(currentRoute.getLongName()).add(t);
            }
        });
    }

    /**
     * This method processes the JSONArray and generates the TrainInfo objects by extracting the desired fields from
     * JSON
     *
     * @param dataArray
     * @param now
     * @param arrayIndex
     * @param routesMap
     * @return
     */
    private TrainInfoModel getTrainInfo(JSONArray dataArray, ZonedDateTime now, int arrayIndex, HashMap<String,
            RouteModel> routesMap) {
        JSONObject current = (JSONObject) dataArray.get(arrayIndex);
        JSONObject attributes = (JSONObject) current.get("attributes");

        String arrivalTime = (String) attributes.get("arrival_time");
        String departureTime = (String) attributes.get("departure_time");
        Long directionId = (Long) attributes.get("direction_id");

        if(departureTime == null) {
            return null;
        }

        long minutesToDeparture = computeMinutesToDeparture(now, arrivalTime, departureTime);

        JSONObject relationships = (JSONObject) current.get("relationships");
        JSONObject route = (JSONObject) relationships.get("route");
        JSONObject routeData = (JSONObject) route.get("data");
        String routeId = (String) routeData.get("id");

        RouteModel currentRoute = routesMap.get(routeId);
        String directionName = "";
        if (directionId == 0) {
            directionName = currentRoute.getDestination_0();
        } else {
            directionName = currentRoute.getDestination_1();
        }

        TrainInfoModel trainInfo = TrainInfoModel.builder()
                .route(routeId)
                .destinationName(directionName)
                .minutesToDeparture(minutesToDeparture)
                .directionId(directionId)
                .build();

        return trainInfo;
    }

    /**
     * This method process the JSONObject and extracts the required information from it and creates a RouteModel object
     *
     * @param r The JSONObject that needs to be processed
     * @return The RouteModel object is returned
     */
    private static RouteModel getRouteModel(JSONObject r) {
        JSONObject current = r;
        String id = (String) current.get("id");

        JSONObject attributes = (JSONObject) current.get("attributes");

        JSONArray directions = (JSONArray) attributes.get("direction_destinations");
        List<Object> directionDirectives = new ArrayList<>();
        directions.forEach(directionDirectives::add);

        String longName = (String) attributes.get("long_name");

        RouteModel currentModel = RouteModel.builder()
                .id(id)
                .longName(longName)
                .destination_0((String) directionDirectives.get(0))
                .destination_1((String) directionDirectives.get(1))
                .build();
        return currentModel;
    }

    /**
     * This method returns the number of minutes remaining before departure. There is special meaning to -1 and 0.
     * When -1 is returned, it means that train has already left. A 0 means that the train will depart in less than a
     * minute
     *
     * @param now           Current ZonedDateTime
     * @param arrivalTime   The arrival time of train
     * @param departureTime The departure time of train
     * @return Returns -1 if train has already left. Returns 0 if departing in less than 1 minute. Otherwise returns the
     * minutes remaining for departure
     */
    private long computeMinutesToDeparture(ZonedDateTime now, String arrivalTime, String departureTime) {
        // create ZonedDateTime for each time
        ZonedDateTime arrivalTimeObj = ZonedDateTime.parse(arrivalTime);
        ZonedDateTime departureTimeObj = ZonedDateTime.parse(departureTime);

        long arrivalSeconds = ChronoUnit.SECONDS.between(arrivalTimeObj, now);
        long departureSeconds = ChronoUnit.SECONDS.between(departureTimeObj, now);

        long minutesToDeparture = 0;

        if (arrivalSeconds > 0) {
            // this means that the arrival time has already passed current time, now check if departure time is
            // still available
            if (departureSeconds < 0) {
                // this means that there is till time for departure
                // here show less than a minute remaining for departure
                return 0;
            } else {
                // this means that train has already left the station
                // here show train has already left
                return -1;
            }
        } else {
            // here compute the number of minutes
            minutesToDeparture = departureSeconds / 60;
            return Math.abs(minutesToDeparture);
        }
    }

    /**
     * This method parses json in string form to JSONObject
     *
     * @param jsonStr The json content in string form
     * @return The parsed JSONObject
     */
    private JSONObject convertToJson(String jsonStr) {
        Object result = JSONValue.parse(jsonStr);
        JSONObject jsonObject = null;

        if (result instanceof JSONObject) {
            jsonObject = (JSONObject) result;
        }

        return jsonObject;
    }

    /**
     * This Comparator is used to sort the entries of HashMap based on the departure time of the first Train on a
     * specific line. This will show the group, where the first train has the least departure time, first
     */
    Comparator<Map.Entry<String, ArrayList<TrainInfoModel>>> k = Comparator.comparing(Map.Entry::getValue, (e1, e2) -> {
        return (int) (e1.get(0).getMinutesToDeparture() - e2.get(0).getMinutesToDeparture());
    });
}
