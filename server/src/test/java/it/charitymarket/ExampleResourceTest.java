package it.charitymarket;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ExampleResourceTest {
    @Test
    void healthEndpointIsAvailable() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200);
    }

}
