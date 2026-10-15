package com.tripmate.shared;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Standalone container readiness check requiring only the Java runtime. */
public final class ReadinessProbe {
    private ReadinessProbe() {}
    public static void main(String[] args) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/actuator/health/readiness"))
            .timeout(Duration.ofSeconds(3)).GET().build();
        var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
            .send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200 || !response.body().contains("\"status\":\"UP\"")) System.exit(1);
    }
}
