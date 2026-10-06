package me.cortex.voxy.client.vulkan;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** A refinement replaces a whole parent only when every child fits and is ready. */
final class LodCoverage {
    private record Candidate<T>(T node, double priority, long order) {}

    static <T> List<T> select(List<T> roots, int limit, ToDoubleFunction<T> priority,
                              Function<T, List<T>> readyChildren) {
        // A manual refinement limit must never remove the outer coarse coverage.
        int capacity = Math.max(limit, roots.size());
        var frontier = new LinkedHashSet<>(roots);
        var queue = new PriorityQueue<Candidate<T>>(
                Comparator.<Candidate<T>>comparingDouble(Candidate::priority).reversed()
                        .thenComparingLong(Candidate::order));
        long order = 0;
        for (T root : roots) {
            double score = priority.applyAsDouble(root);
            if (score > 0) queue.add(new Candidate<>(root, score, order++));
        }
        while (!queue.isEmpty()) {
            var candidate = queue.remove();
            if (!frontier.contains(candidate.node())) continue;
            var children = readyChildren.apply(candidate.node());
            if (children == null || children.isEmpty()) continue;
            if ((long) frontier.size() - 1 + children.size() > capacity) continue;
            frontier.remove(candidate.node());
            for (T child : children) {
                frontier.add(child);
                double score = priority.applyAsDouble(child);
                if (score > 0) queue.add(new Candidate<>(child, score, order++));
            }
        }
        return new ArrayList<>(frontier);
    }
}
