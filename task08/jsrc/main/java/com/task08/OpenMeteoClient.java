package com.task08;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import java.io.IOException;

public class OpenMeteoClient {
    private static final String API_URL = "https://api.open-meteo.com/v1/forecast";

    public String getWeatherForecast(double latitude, double longitude) throws IOException {
        String url = String.format("%s?latitude=%f&longitude=%f&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m", API_URL, latitude, longitude);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                return EntityUtils.toString(entity);
            }
        }
    }
}
