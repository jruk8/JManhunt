package com.jruk8.jmanhunt.loot;

import java.util.List;

public record Entry(String type, String name, int weight, List<Function> functions) {}