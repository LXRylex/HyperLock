// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps Android's paired package and driver lists aligned. */
public final class AngleSelections {
    private final LinkedHashMap<String, String> choices = new LinkedHashMap<>();
    public static AngleSelections parse(String packages, String values) {
        AngleSelections result = new AngleSelections();
        if (empty(packages) && empty(values)) return result;
        if (empty(packages) || empty(values)) throw new IllegalArgumentException("Driver settings are incomplete");
        String[] names = packages.split(",", -1), drivers = values.split(",", -1);
        if (names.length != drivers.length) throw new IllegalArgumentException("Driver settings are mismatched");
        for (int i = 0; i < names.length; i++) {
            if (!valid(names[i]) || drivers[i].isEmpty() || result.choices.containsKey(names[i]))
                throw new IllegalArgumentException("Driver settings are invalid");
            result.choices.put(names[i], drivers[i]);
        }
        return result;
    }
    private static boolean empty(String value) { return value == null || value.isEmpty() || value.equals("null"); }
    private static boolean valid(String name) { return name.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*"); }
    public boolean usesAngle(String name) { return "angle".equals(choices.get(name)); }
    public void setAngle(String name, boolean enabled) {
        if (!valid(name)) throw new IllegalArgumentException("Invalid package name");
        if (enabled) choices.put(name, "angle");
        else if (usesAngle(name)) choices.remove(name);
    }
    public String packages() { return String.join(",", choices.keySet()); }
    public String values() { return String.join(",", choices.values()); }
}
