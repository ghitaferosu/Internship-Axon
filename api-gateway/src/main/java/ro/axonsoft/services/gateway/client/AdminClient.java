package ro.axonsoft.services.gateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "admin-feedingJob",url = "${services.event-service.url}")
public interface AdminClient {
    @PostMapping("/admin/venues")
    JsonNode seedVenue(@RequestBody JsonNode request);

    @PostMapping("/admin/performers")
    JsonNode seedPerformers(@RequestBody JsonNode request);

    @PostMapping("/admin/events")
    JsonNode seedEvents(@RequestBody JsonNode request);
}
