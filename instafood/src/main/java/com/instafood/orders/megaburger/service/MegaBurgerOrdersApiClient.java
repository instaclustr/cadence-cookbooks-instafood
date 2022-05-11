package com.instafood.orders.megaburger.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instafood.orders.megaburger.MegaBurgerFoodOrder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MegaBurgerOrdersApiClient {

    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public MegaBurgerOrdersApiClient() {
        objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        baseUrl = "http://localhost:8080";
    }

    public MegaBurgerFoodOrder create(MegaBurgerFoodOrder megaBurgerFoodOrder) {
        HttpRequest httpRequest = getMegaBurgerHttpRequestBuilder("/orders")
                .POST(HttpRequest.BodyPublishers.ofString(encodeAsString(megaBurgerFoodOrder)))
                .build();

        return parseResponse(sendRequest(httpRequest), MegaBurgerFoodOrder.class);
    }

    private HttpRequest.Builder getMegaBurgerHttpRequestBuilder(String path) {
        try {
            return HttpRequest.newBuilder(new URI(baseUrl + path))
                    .header("content-type", "application/json");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String encodeAsString(MegaBurgerFoodOrder megaBurgerFoodOrder) {
        try {
            return objectMapper.writeValueAsString(megaBurgerFoodOrder);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpResponse<String> sendRequest(HttpRequest httpRequest) {
        try {
            return HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T parseResponse(HttpResponse<String> response, Class<T> valueType) {
        try {
            return objectMapper.readValue(response.body(), valueType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public MegaBurgerFoodOrder getById(Integer orderId) {
        HttpRequest httpRequest = getMegaBurgerHttpRequestBuilder("/orders/" + orderId)
                .GET().build();

        return parseResponse(sendRequest(httpRequest), MegaBurgerFoodOrder.class);
    }

    public List<MegaBurgerFoodOrder> getAll() {
        HttpRequest httpRequest = getMegaBurgerHttpRequestBuilder("/orders")
                .GET().build();

        return Arrays.stream(parseResponse(sendRequest(httpRequest), MegaBurgerFoodOrder[].class))
                .collect(Collectors.toList());
    }

    public void updateStatusAndEta(Integer orderId, String status, Integer etaInMinutes) {
        HttpRequest httpRequest = getMegaBurgerHttpRequestBuilder("/orders/" + orderId)
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{" +
                        "\"status\": \"" + status + "\"," +
                        "\"eta_minutes\": " + etaInMinutes +
                        "}"))
                .build();

        sendRequest(httpRequest);
    }

    public void updateStatus(Integer orderId, String status) {
        HttpRequest httpRequest = getMegaBurgerHttpRequestBuilder("/orders/" + orderId)
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"status\": \"" + status + "\"}"))
                .build();

        sendRequest(httpRequest);
    }

}
