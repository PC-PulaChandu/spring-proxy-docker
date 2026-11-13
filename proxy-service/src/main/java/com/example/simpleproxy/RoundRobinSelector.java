package com.example.simpleproxy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "proxy.selector", havingValue = "round-robin", matchIfMissing = true)
public class RoundRobinSelector implements UpstreamSelector {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public String select(List<String> urls) {
        int index = Math.floorMod(counter.getAndIncrement(), urls.size());
        return urls.get(index);
    }
}