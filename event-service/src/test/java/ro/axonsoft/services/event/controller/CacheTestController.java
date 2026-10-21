package ro.axonsoft.services.event.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ro.axonsoft.services.event.service.CacheTestService;

@RestController
public class CacheTestController {
    private final CacheTestService cacheTestService;
    public CacheTestController(CacheTestService cacheTestService) {
        this.cacheTestService = cacheTestService;
    }
    @GetMapping("/api/cache-test/{input}")
    public String test(@PathVariable String input) {
        return cacheTestService.writeSomething(input);
    }

}
