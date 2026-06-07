package com.jackutil.aastemplateapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonCleaner {

    public static JsonNode excludePaths(@NonNull JsonNode json, @NonNull List<String> paths) {
        if (paths.isEmpty()) {
            return json;
        }

        JsonNode copy = json.deepCopy();
        excludePaths(copy, paths, "$");
        return copy;
    }

    private static void excludePaths(JsonNode node, List<String> paths, String currentPath) {
        switch (node) {
            case ObjectNode objectNode -> {
                List<String> keys = new ArrayList<>();
                objectNode.fieldNames().forEachRemaining(keys::add);

                for (String key : keys) {
                    String childPath = currentPath + "." + key;
                    if (paths.contains(childPath)) {
                        objectNode.remove(key);
                    } else {
                        excludePaths(objectNode.get(key), paths, childPath);
                    }
                }
            }
            case ArrayNode arrayNode -> {
                arrayNode.forEach(element -> excludePaths(element, paths, currentPath));
            }
            default -> {

            }
        }
    }

    public static JsonNode replacePaths(@NonNull JsonNode json, Map<String, String> replacements) {
        if (replacements == null || replacements.isEmpty()) {
            return json;
        }
        JsonNode copy = json.deepCopy();
        replacePaths(copy, replacements, "$");
        return copy;
    }

    private static void replacePaths(JsonNode node, Map<String, String> replacements, String currentPath) {
        switch (node) {
            case ObjectNode objectNode -> {
                objectNode.fieldNames().forEachRemaining(key -> {
                    String childPath = currentPath + "." + key;
                    JsonNode childNode = objectNode.get(key);

                    if (replacements.containsKey(childPath) && childNode.isValueNode()) {
                        objectNode.put(key, replacements.get(childPath));
                    } else {
                        replacePaths(childNode, replacements, childPath);
                    }
                });
            }
            case ArrayNode arrayNode -> {
                arrayNode.forEach(element -> replacePaths(element, replacements, currentPath));
            }
            default -> {

            }
        }
    }

    public static JsonNode cleanJson(@NonNull JsonNode json) {
        JsonNode copy = json.deepCopy();
        deepClean(copy);
        return copy;
    }

    private static void deepClean(JsonNode node) {
        switch (node) {
            case ObjectNode objectNode -> {
                objectNode.properties().forEach(entry -> deepClean(entry.getValue()));
                objectNode.removeIf(JsonCleaner::isEmpty);
            }
            case ArrayNode arrayNode -> {
                arrayNode.forEach(JsonCleaner::deepClean);
                arrayNode.removeIf(JsonCleaner::isEmpty);
            }
            default -> {

            }
        }
    }

    private static boolean isEmpty(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return true;
        }
        return node.isContainerNode() && node.isEmpty();
    }
}
