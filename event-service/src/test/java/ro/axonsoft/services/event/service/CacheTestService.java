package ro.axonsoft.services.event.service;


import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CacheTestService {
    @Cacheable("test-cache")
    public String writeSomething(String something){
        System.out.println("writing for "+something);
        try{
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Rezultat pentru "+something;
    }
}
