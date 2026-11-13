package com.example.simpleproxy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamSelectorsTest {

    private final List<String> urls = List.of("http://a", "http://b", "http://c");

    @Test
    void roundRobinCyclesThroughAllUrlsInOrder() {
        RoundRobinSelector selector = new RoundRobinSelector();

        assertThat(selector.select(urls)).isEqualTo("http://a");
        assertThat(selector.select(urls)).isEqualTo("http://b");
        assertThat(selector.select(urls)).isEqualTo("http://c");
        assertThat(selector.select(urls)).isEqualTo("http://a");
    }

    @Test
    void roundRobinStillWorksWhenTheListShrinks() {
        RoundRobinSelector selector = new RoundRobinSelector();
        selector.select(urls);
        selector.select(urls);

        assertThat(selector.select(List.of("http://only"))).isEqualTo("http://only");
    }

    @Test
    void randomAlwaysReturnsAMemberOfTheList() {
        RandomSelector selector = new RandomSelector();

        for (int i = 0; i < 50; i++) {
            assertThat(urls).contains(selector.select(urls));
        }
    }
}