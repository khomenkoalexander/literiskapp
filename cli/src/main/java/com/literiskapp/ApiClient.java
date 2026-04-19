package com.literiskapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ApiClient {

    private final Config config;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public ApiClient(Config config) {
        this.config = config;
        this.http = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void get(String path) throws Exception {
        HttpRequest request = baseRequest(path).GET().build();
        send(request);
    }

    public void post(String path, Path jsonFile) throws Exception {
        String body = Files.readString(jsonFile);
        Object parsed = mapper.readValue(body, Object.class);
        if (!(parsed instanceof List)) {
            System.err.println("Error: JSON file must contain an array of objects.");
            System.exit(1);
        }
        HttpRequest request = baseRequest(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        send(request);
    }

    public void delete(String path) throws Exception {
        HttpRequest request = baseRequest(path).DELETE().build();
        send(request);
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl + path))
                .header("Authorization", "Bearer " + config.bearerToken);
    }

    private void send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        System.out.println("HTTP " + status);
        String body = response.body();
        if (body != null && !body.isBlank()) {
            try {
                Object obj = mapper.readValue(body, Object.class);
                System.out.println(mapper.writeValueAsString(obj));
            } catch (Exception e) {
                System.out.println(body);
            }
        }
        if (status >= 400) {
            System.exit(1);
        }
    }
}
