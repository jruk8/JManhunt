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

    /**
     * Rewinds to an already-visited node: truncates the stack to it and
     * resets the step number to the depth, so back history matches the
     * step. False when the node was never visited (caller pushes fresh).
     */
    public boolean revisit(String node) {
        if (!stack.contains(node)) {
            return false;
        }
        while (!node.equals(stack.peek())) {
            stack.pop();
        }
        shownCount = stack.size();
        return true;
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

    /** Steps back one shown dialogue, never below the first. */
    public void backed() {
        shownCount = Math.max(1, shownCount - 1);
    }

    public long lastAnswerMillis() {
        return lastAnswerMillis;
    }

    public void answered(long now) {
        lastAnswerMillis = now;
    }
}
