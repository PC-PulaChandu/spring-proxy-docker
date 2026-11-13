package com.example.simpleproxy;

import java.util.List;

public interface UpstreamSelector {
    String select(List<String> urls);
}