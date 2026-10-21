// controller/AdminController.java
package ro.axonsoft.services.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ro.axonsoft.services.gateway.client.AdminClient;
import ro.axonsoft.services.gateway.client.EventClient;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminClient adminClient;

    @PostMapping("/venues")
    public JsonNode seedVenue(@RequestBody JsonNode request) {
        return adminClient.seedVenue(request);
    }

    @PostMapping("/performers")
    public JsonNode seedPerformers(@RequestBody JsonNode request) {
        return adminClient.seedPerformers(request);
    }

    @PostMapping("/events")
    public JsonNode seedEvents(@RequestBody JsonNode request) {
        return adminClient.seedEvents(request);
    }
}