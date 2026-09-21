package com.jruk8.jmanhunt.tutorial;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** One player's tutorial progress: node stack plus dialogue counters. */
public final class TutorialSession {

    private final UUID playerId;
    private final Deque<String> stack = new ArrayDeque<>();
    private int shownCount;
    private long lastAnswerMillis;

    TutorialSession(UUID playerId, String startNode, long now) {
        this.playerId = playerId;
        this.stack.push(startNode);
        this.lastAnswerMillis = now;
    }

    public UUID playerId() {
        return playerId;
    }

    public String current() {
        return stack.peek();
    }

    public int depth() {
        return stack.size();
    }

    public void push(String node) {
        stack.push(node);
    }

    /** Pops one node, never the last one. */
    public void pop() {
        if (stack.size() > 1) {
            stack.pop();
        }
    }

    public int shownCount() {
        return shownCount;
    }

    public void shown() {
        shownCount++;
    }

    public long lastAnswerMillis() {
        return lastAnswerMillis;
    }

    public void answered(long now) {
        lastAnswerMillis = now;
    }
}
