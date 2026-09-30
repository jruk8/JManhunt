package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.util.Vector;

/**
 * Vector math for JMHScript: the SSOT behind {@code <vec.add>},
 * {@code <vec.sub>}, {@code <vec.mult>}, {@code <vec.normalize>},
 * {@code <vec.sqrdist>}, {@code <vec.dist>}, {@code <vec.dot>},
 * {@code <vec.cross>}, {@code <loc.shift>}, and {@code <pdir>}.
 * Vector args accept 3-element lists or 6-element location
 * primitives (first three indices); anything else warns plus
 * {@code null} at the tag layer. Outputs format with
 * {@link TagMath#formatNumber}. No Bukkit types except the
 * direction read, which arrives through the roster.
 */
public final class TagVectors {

    private TagVectors() {
    }

    /** Element-wise sum. */
    public static double[] add(double[] first, double[] second) {
        return new double[] {first[0] + second[0], first[1] + second[1], first[2] + second[2]};
    }

    /** Element-wise difference: the direction from B to A. */
    public static double[] sub(double[] first, double[] second) {
        return new double[] {first[0] - second[0], first[1] - second[1], first[2] - second[2]};
    }

    /** Every element scaled. */
    public static double[] mult(double[] vec, double scalar) {
        return new double[] {vec[0] * scalar, vec[1] * scalar, vec[2] * scalar};
    }

    /** Unit vector; the zero vector yields {@code [0, 0, 0]}. */
    public static double[] normalize(double[] vec) {
        double length = Math.sqrt(dot(vec, vec));
        if (length == 0) {
            return new double[] {0, 0, 0};
        }
        return new double[] {vec[0] / length, vec[1] / length, vec[2] / length};
    }

    /** Squared Euclidean distance. */
    public static double sqrdist(double[] first, double[] second) {
        double dx = first[0] - second[0];
        double dy = first[1] - second[1];
        double dz = first[2] - second[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /** Euclidean distance. */
    public static double dist(double[] first, double[] second) {
        return Math.sqrt(sqrdist(first, second));
    }

    /** Dot product scalar. */
    public static double dot(double[] first, double[] second) {
        return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
    }

    /** Cross product vector. */
    public static double[] cross(double[] first, double[] second) {
        return new double[] {
                first[1] * second[2] - first[2] * second[1],
                first[2] * second[0] - first[0] * second[2],
                first[0] * second[1] - first[1] * second[0]};
    }

    /** Canonical triple string: elements joined with {@code ", "} in brackets. */
    public static String formatVec(double[] vec) {
        return "[" + TagMath.formatNumber(vec[0]) + ", " + TagMath.formatNumber(vec[1]) + ", "
                + TagMath.formatNumber(vec[2]) + "]";
    }

    /**
     * Pure vector-op entry for tag evaluation: splits, validates,
     * and applies. Misuse warns plus {@code "null"}; a zero vector
     * normalizes silently.
     */
    static String resolve(String tag, String name, String args, TagContext context) {
        List<String> segments = TagLists.splitTopLevel(args);
        if (segments.size() != arity(name)) {
            context.scope().warn("Tag <" + name + "> needs " + arity(name) + " args like "
                    + example(name) + ": " + tag);
            return "null";
        }
        List<String> parsed = new ArrayList<>();
        for (String segment : segments) {
            Optional<String> item = CommandPlaceholders.parsePickItem(segment);
            if (item.isEmpty() && !segment.isBlank()) {
                context.scope().warn("Tag <" + name + "> mixes quotes: " + tag);
                return "null";
            }
            parsed.add(item.orElse(""));
        }
        return switch (name) {
            case "pdir" -> pdir(tag, parsed.get(0), context);
            case "loc.shift" -> shift(tag, parsed, context);
            case "vec.mult" -> multOp(tag, parsed, context);
            case "vec.normalize" -> normalizeOp(tag, parsed.get(0), context);
            default -> binaryOp(tag, name, parsed, context);
        };
    }

    /** Unit look direction for one player; offline players resolve null silently. */
    private static String pdir(String tag, String raw, TagContext context) {
        if (raw.isBlank()) {
            context.scope().warn("Tag <pdir> needs a player like <pdir:Steve>: " + tag);
            return "null";
        }
        Optional<Vector> direction = context.roster().lookDirection(raw.strip());
        if (direction.isEmpty()) {
            return "null";
        }
        Vector vec = direction.get();
        return formatVec(normalize(new double[] {vec.getX(), vec.getY(), vec.getZ()}));
    }

    /**
     * Base location plus direction times distance as a canonical
     * 6-element list. The base must be a full primitive; the
     * direction is used as given (callers compose with
     * {@code <vec.normalize>}); world, pitch, and yaw carry over
     * verbatim from the base.
     */
    private static String shift(String tag, List<String> parsed, TagContext context) {
        List<String> base = TagLists.parse(parsed.get(0));
        if (!TagLists.isList(parsed.get(0)) || base.size() != 6) {
            context.scope().warn("Tag <loc.shift> needs a 6-element base like "
                    + "[x, y, z, world, pitch, yaw]: " + tag);
            return "null";
        }
        Optional<double[]> origin = numbers(tag, "loc.shift", base.subList(0, 3), context);
        Optional<double[]> direction = coerce(tag, "loc.shift", parsed.get(1), context);
        Optional<Double> distance = scalar(tag, "loc.shift", parsed.get(2), context);
        if (origin.isEmpty() || direction.isEmpty() || distance.isEmpty()) {
            return "null";
        }
        double[] from = origin.get();
        double[] dir = direction.get();
        double far = distance.get();
        return "[" + TagMath.formatNumber(from[0] + dir[0] * far) + ", "
                + TagMath.formatNumber(from[1] + dir[1] * far) + ", "
                + TagMath.formatNumber(from[2] + dir[2] * far) + ", " + base.get(3) + ", "
                + base.get(4) + ", " + base.get(5) + "]";
    }

    /** Vector scaled; a non-numeric scalar warns plus null. */
    private static String multOp(String tag, List<String> parsed, TagContext context) {
        Optional<double[]> vec = coerce(tag, "vec.mult", parsed.get(0), context);
        Optional<Double> scalar = scalar(tag, "vec.mult", parsed.get(1), context);
        if (vec.isEmpty() || scalar.isEmpty()) {
            return "null";
        }
        return formatVec(mult(vec.get(), scalar.get()));
    }

    /** Unit vector; zero yields {@code [0, 0, 0]} silently. */
    private static String normalizeOp(String tag, String raw, TagContext context) {
        Optional<double[]> vec = coerce(tag, "vec.normalize", raw, context);
        if (vec.isEmpty()) {
            return "null";
        }
        return formatVec(normalize(vec.get()));
    }

    /** One binary op over two coerced vectors. */
    private static String binaryOp(String tag, String op, List<String> parsed,
            TagContext context) {
        Optional<double[]> first = coerce(tag, op, parsed.get(0), context);
        Optional<double[]> second = coerce(tag, op, parsed.get(1), context);
        if (first.isEmpty() || second.isEmpty()) {
            return "null";
        }
        double[] left = first.get();
        double[] right = second.get();
        return switch (op) {
            case "vec.add" -> formatVec(add(left, right));
            case "vec.sub" -> formatVec(sub(left, right));
            case "vec.sqrdist" -> TagMath.formatNumber(sqrdist(left, right));
            case "vec.dist" -> TagMath.formatNumber(dist(left, right));
            case "vec.dot" -> TagMath.formatNumber(dot(left, right));
            default -> formatVec(cross(left, right));
        };
    }

    /**
     * First three indices of a 3-element list or 6-element
     * primitive as doubles. Anything else warns plus empty.
     */
    private static Optional<double[]> coerce(String tag, String op, String raw,
            TagContext context) {
        if (!TagLists.isList(raw)) {
            context.scope().warn("Tag <" + op + "> needs a vector like [x, y, z]: " + tag);
            return Optional.empty();
        }
        List<String> items = TagLists.parse(raw);
        if (items.size() != 3 && items.size() != 6) {
            context.scope().warn("Tag <" + op + "> needs a vector like [x, y, z]: " + tag);
            return Optional.empty();
        }
        return numbers(tag, op, items.subList(0, 3), context);
    }

    /** Three finite numbers; misuse warns plus empty. */
    private static Optional<double[]> numbers(String tag, String op, List<String> items,
            TagContext context) {
        double[] vec = new double[3];
        for (int index = 0; index < 3; index++) {
            try {
                vec[index] = Double.parseDouble(items.get(index).strip());
            } catch (NumberFormatException invalid) {
                context.scope().warn("Tag <" + op + "> needs numbers like [1, 2, 3]: " + tag);
                return Optional.empty();
            }
            if (!Double.isFinite(vec[index])) {
                context.scope().warn("Tag <" + op + "> needs numbers like [1, 2, 3]: " + tag);
                return Optional.empty();
            }
        }
        return Optional.of(vec);
    }

    /** One finite scalar; misuse warns plus empty. */
    private static Optional<Double> scalar(String tag, String op, String raw,
            TagContext context) {
        double scalar;
        try {
            scalar = Double.parseDouble(raw.strip());
        } catch (NumberFormatException invalid) {
            context.scope().warn("Tag <" + op + "> needs a number like <" + op + ":vec,5>: "
                    + tag);
            return Optional.empty();
        }
        if (!Double.isFinite(scalar)) {
            context.scope().warn("Tag <" + op + "> needs a number like <" + op + ":vec,5>: "
                    + tag);
            return Optional.empty();
        }
        return Optional.of(scalar);
    }

    /**
     * Edit-time arity for the vector ops: top-level split, quote
     * hygiene, plus the per-op shape. Mirrors the runtime warns.
     */
    static Optional<String> opError(String name, String args) {
        int count = arity(name);
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs " + count + " args like "
                    + example(name) + ".");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != count) {
            return Optional.of("Tag <" + name + "> needs " + count + " args like "
                    + example(name) + ".");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty() && !part.isBlank()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    private static int arity(String name) {
        return switch (name) {
            case "pdir", "vec.normalize" -> 1;
            case "loc.shift" -> 3;
            default -> 2;
        };
    }

    private static String example(String name) {
        return switch (name) {
            case "pdir" -> "<pdir:player>";
            case "vec.add" -> "<vec.add:a,b>";
            case "vec.sub" -> "<vec.sub:a,b>";
            case "vec.mult" -> "<vec.mult:vec,scalar>";
            case "vec.normalize" -> "<vec.normalize:vec>";
            case "vec.sqrdist" -> "<vec.sqrdist:a,b>";
            case "vec.dist" -> "<vec.dist:a,b>";
            case "vec.dot" -> "<vec.dot:a,b>";
            case "vec.cross" -> "<vec.cross:a,b>";
            default -> "<loc.shift:location,direction,distance>";
        };
    }
}
