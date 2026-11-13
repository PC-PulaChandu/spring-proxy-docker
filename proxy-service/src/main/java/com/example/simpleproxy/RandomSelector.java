package com.example.simpleproxy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "proxy.selector", havingValue = "random")
public class RandomSelector implements UpstreamSelector {

    @Override
    public String select(List<String> urls) {
        return urls.get(ThreadLocalRandom.current().nextInt(urls.size()));
    }
}