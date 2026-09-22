package com.example.issuedjinn.rest.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for bootstrap configuration and first-run experience using @QuarkusTest + RestAssured with AssertJ assertions.
 */
@QuarkusTest
public class BootstrapConfigTest {

    @Test
    public void testFirstRunExperience_IssuesAndLabelsResponseStructure() {
        // Verify GET /api/issues returns issues array
        Response issuesResponse = given()
                .when().get("/api/issues")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        // Verify the response has the expected structure with 'issues' key
        String issuesResponseBody = issuesResponse.getBody().asString();
        assertThat(issuesResponseBody).contains("\"issues\"");

        // Verify GET /api/labels returns labels array
        Response labelsResponse = given()
                .when().get("/api/labels")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        // Verify the response has the expected structure with 'labels' key
        String labelsResponseBody = labelsResponse.getBody().asString();
        assertThat(labelsResponseBody).contains("\"labels\"");
    }

    @Test
    public void testHealthEndpointResponds() {
        Response response = given()
                .when().get("/api/health")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        assertThat(response.jsonPath().getString("status")).isEqualTo("ok");
    }
}
