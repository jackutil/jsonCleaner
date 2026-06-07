package com.jackutil.aastemplateapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonCleanerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Test utility to parse a raw JSON text block into a JsonNode semantically.
     * Throws an unchecked exception so it can be cleanly used inline inside assertions.
     */
    private JsonNode json(String jsonString) {
        try {
            return mapper.readTree(jsonString);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Malformed JSON provided in test case block", e);
        }
    }

    @Test
    void shouldPruneFlatEmptyFieldsFromObject() throws JsonProcessingException {
        // Given
        String json = """
            {
                "validString": "hello",
                "validNumber": 42,
                "emptyString": "",
                "emptyObject": {},
                "emptyArray": [],
                "nullField": null
            }
            """;
        JsonNode input = mapper.readTree(json);

        // When
        JsonNode result = JsonCleaner.cleanJson(input);

        // Then
        assertThat(result).isEqualTo(json("""
            {
                "validString": "hello",
                "validNumber": 42,
                "emptyString": ""
            }
            """));
    }

    @Test
    void shouldCascadePruningForDeeplyNestedEmptyObjects() throws JsonProcessingException {
        // Given
        String json = """
            {
                "keepMe": "save",
                "layer1": {
                    "layer2": {
                        "layer3": {}
                    }
                }
            }
            """;
        JsonNode input = mapper.readTree(json);

        // When
        JsonNode result = JsonCleaner.cleanJson(input);

        // Then
        assertThat(result).isEqualTo(json("""
            {
                "keepMe": "save"
            }
            """));
    }

    @Test
    void shouldCleanArraysAndRemoveEmptyElements() throws JsonProcessingException {
        // Given
        String json = """
            {
                "list": [
                    "validItem",
                    null,
                    [],
                    {},
                    { "nestedEmpty": [] }
                ]
            }
            """;
        JsonNode input = mapper.readTree(json);

        // When
        JsonNode result = JsonCleaner.cleanJson(input);

        // Then
        assertThat(result).isEqualTo(json("""
            {
                "list": [
                    "validItem"
                ]
            }
            """));
    }

    @Test
    void shouldNotMutateOriginalJsonNode() throws JsonProcessingException {
        // Given
        String json = """
            {
                "keep": "me",
                "remove": []
            }
            """;
        JsonNode originalInput = mapper.readTree(json);

        // When
        JsonNode result = JsonCleaner.cleanJson(originalInput);

        // Then
        // 1. Ensure the output is actually pruned
        assertThat(result.has("remove")).isFalse();

        // 2. Ensure the original input object was not mutated semantically
        assertThat(originalInput).isEqualTo(json("""
            {
                "keep": "me",
                "remove": []
            }
            """));

        // 3. Ensure they are completely different object references in memory
        assertThat(result).isNotSameAs(originalInput);
    }

    @Test
    void shouldHandleComplexRealWorldDataModel() throws JsonProcessingException {
        // Given
        String json = """
            {
                "id": 12345,
                "name": "Test Payload",
                "metadata": {
                    "version": "1.0",
                    "deprecatedTags": [],
                    "tracing": null
                },
                "items": [
                    {
                        "itemId": 1,
                        "subItems": []
                    },
                    {
                        "itemId": 2,
                        "subItems": ["A", "B"]
                    }
                ]
            }
            """;
        JsonNode input = mapper.readTree(json);

        // When
        JsonNode result = JsonCleaner.cleanJson(input);

        // Then
        assertThat(result).isEqualTo(json("""
            {
                "id": 12345,
                "name": "Test Payload",
                "metadata": {
                    "version": "1.0"
                },
                "items": [
                    {
                        "itemId": 1
                    },
                    {
                        "itemId": 2,
                        "subItems": [
                            "A",
                            "B"
                        ]
                    }
                ]
            }
            """));
    }

    /* --- ADDED TESTS FOR EXCLUDE PATHS FUNCTIONALITY --- */

    @Test
    void shouldExcludeSpecificPathsWithoutCleaningContainers() throws JsonProcessingException {
        // Given
        String json = """
            {
                "id": 999,
                "secretToken": "xyz123",
                "meta": {
                    "author": "Jakob",
                    "internalId": "INT-88"
                },
                "unrelatedEmpty": {}
            }
            """;
        JsonNode input = mapper.readTree(json);
        List<String> excludes = List.of("$.secretToken", "$.meta.internalId");

        // When
        JsonNode result = JsonCleaner.excludePaths(input, excludes);

        // Then
        assertThat(result).isEqualTo(json("""
            {
                "id": 999,
                "meta": {
                    "author": "Jakob"
                },
                "unrelatedEmpty": {}
            }
            """));
    }

    @Test
    void shouldExcludeFieldsTransparentlyThroughArrays() throws JsonProcessingException {
        // Given
        String json = """
            {
                "batchId": "B-1",
                "records": [
                    { "id": 1, "sensitiveData": "hide-me", "value": "A" },
                    { "id": 2, "sensitiveData": "delete-me", "value": "B" }
                ]
            }
            """;
        JsonNode input = mapper.readTree(json);
        List<String> excludes = List.of("$.records.sensitiveData");

        // When
        JsonNode result = JsonCleaner.excludePaths(input, excludes);

        // Then
        assertThat(result).isEqualTo(json("""
            {
                "batchId": "B-1",
                "records": [
                    { "id": 1, "value": "A" },
                    { "id": 2, "value": "B" }
                ]
            }
            """));
    }

    @Test
    void shouldExecuteFullAssertJPipelineSequentially() throws JsonProcessingException {
        // Given
        String json = """
            {
                "transactionId": "TX-100",
                "security": {
                    "salt": "NaCl",
                    "hash": "SHA-256"
                },
                "payload": "data"
            }
            """;
        JsonNode input = mapper.readTree(json);
        List<String> excludes = List.of("$.security.salt", "$.security.hash");

        // When
        JsonNode excludedNode = JsonCleaner.excludePaths(input, excludes);
        JsonNode finalizedNode = JsonCleaner.cleanJson(excludedNode);

        // Then
        assertThat(finalizedNode).isEqualTo(json("""
            {
                "transactionId": "TX-100",
                "payload": "data"
            }
            """));
    }

    @Test
    void shouldReturnSameReferenceAndNotMutateWhenPathsAreEmptyOrNull() throws JsonProcessingException {
        // Given
        String json = "{ \"test\": \"value\" }";
        JsonNode input = mapper.readTree(json);

        // When
        JsonNode resultWithNull = JsonCleaner.excludePaths(input, new ArrayList<>());
        JsonNode resultWithEmpty = JsonCleaner.excludePaths(input, Collections.emptyList());

        // Then
        assertThat(resultWithNull).isSameAs(input);
        assertThat(resultWithEmpty).isSameAs(input);
    }

    @Test
    void shouldNotMutateOriginalJsonNodeDuringExclusion() throws JsonProcessingException {
        // Given
        String json = "{ \"id\": 1, \"nukeMe\": \"kaboom\" }";
        JsonNode originalInput = mapper.readTree(json);

        // When
        JsonNode result = JsonCleaner.excludePaths(originalInput, List.of("$.nukeMe"));

        // Then
        assertThat(result.has("nukeMe")).isFalse();
        assertThat(originalInput.has("nukeMe")).isTrue();
        assertThat(result).isNotSameAs(originalInput);
    }

    @Test
    void shouldReplaceTargetedStringsAndNumbersWithPlaceholders() throws JsonProcessingException {
        // Given
        String json = """
            {
                "userId": 582910,
                "username": "jdoe",
                "session": {
                    "token": "ca76-4f82-b11a",
                    "createdAt": "2026-06-07T12:00:00Z"
                }
            }
            """;
        JsonNode input = mapper.readTree(json);
        Map<String, String> replacements = Map.of(
                "$.userId", "REPLACED_ID",
                "$.session.token", "REPLACED_TOKEN"
        );

        // When
        JsonNode result = JsonCleaner.replacePaths(input, replacements);

        // Then
        assertThat(result).isEqualTo(json("""
            {
                "userId": "REPLACED_ID",
                "username": "jdoe",
                "session": {
                    "token": "REPLACED_TOKEN",
                    "createdAt": "2026-06-07T12:00:00Z"
                }
            }
            """));
    }

    @Test
    void shouldReplaceValuesUniformlyInsideArrays() throws JsonProcessingException {
        // Given
        String json = """
            {
                "items": [
                    { "id": 101, "name": "Item A" },
                    { "id": 102, "name": "Item B" }
                ]
            }
            """;
        JsonNode input = mapper.readTree(json);
        Map<String, String> replacements = Map.of("$.items.id", "DYNAMIC_ID");

        // When
        JsonNode result = JsonCleaner.replacePaths(input, replacements);

        // Then
        assertThat(result).isEqualTo(json("""
            {
                "items": [
                    { "id": "DYNAMIC_ID", "name": "Item A" },
                    { "id": "DYNAMIC_ID", "name": "Item B" }
                ]
            }
            """));
    }

    @Test
    void shouldFailAssertionIfAReplacedFieldGoesMissing() throws JsonProcessingException {
        // Given
        String actualJsonFromBuggyApp = """
            {
                "username": "jdoe"
            }
            """;
        JsonNode actualNode = mapper.readTree(actualJsonFromBuggyApp);
        Map<String, String> replacements = Map.of("$.userId", "DYNAMIC_ID");

        // When
        JsonNode processedActual = JsonCleaner.replacePaths(actualNode, replacements);

        // Then
        assertThat(processedActual).isNotEqualTo(json("""
            {
                "userId": "DYNAMIC_ID",
                "username": "jdoe"
            }
            """));
    }
}
