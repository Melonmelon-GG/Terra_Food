package com.dayan.food.service;

import com.dayan.food.mapper.FoodMapper;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
public class DiscoveryCountService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.dayan.food.cache.DiscoveryVersion versions;
    @org.springframework.beans.factory.annotation.Value("${app.discovery-cache.enabled:false}") private boolean cacheEnabled;
    private record Entry(long deadline,int total){}
    private final java.util.Map<String,Entry> counts=new java.util.LinkedHashMap<>(256,0.75f,true);
    private final FoodMapper mapper;
    public DiscoveryCountService(FoodMapper mapper) { this.mapper = mapper; }

    public int count(List<String> tokens, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, BigDecimal minLatitude,
            BigDecimal maxLatitude, BigDecimal minLongitude, BigDecimal maxLongitude) {
        // The enclosing discovery gate bounds database concurrency. Arbitrary text/viewports are not admitted.
        boolean admitted=cacheEnabled&&versions!=null&&tokens.isEmpty()&&minLatitude==null;
        long version=admitted?versions.current():0;
        String key=version+":"+java.util.Arrays.deepToString(new Object[]{regionId,tasteIds,ingredientIds,cuisineIds});
        if(admitted){synchronized(counts){var entry=counts.get(key);if(entry!=null&&entry.deadline()>System.nanoTime())return entry.total();}}
        int total=mapper.countFilteredCatalog(tokens, regionId, tasteIds, ingredientIds, cuisineIds,
                minLatitude, maxLatitude, minLongitude, maxLongitude);
        if(admitted&&versions.current()==version){synchronized(counts){counts.put(key,new Entry(System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30),total));while(counts.size()>256)counts.remove(counts.keySet().iterator().next());}}
        return total;
    }
}
