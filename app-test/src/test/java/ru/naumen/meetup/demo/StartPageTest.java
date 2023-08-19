package ru.naumen.meetup.demo;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.containsString;

class StartPageTest {
    private static final String baseURI;

    static {
        var port = Optional.ofNullable(System.getProperty("app.port")).orElse("8080");
        baseURI = "http://localhost:" + Integer.parseInt(port);
        RestAssured.baseURI = baseURI;
    }

    @Test
    void test() {
        //@formatter:off
        given()
        .expect()
                .statusCode(SC_OK)
                .body(containsString("Naumen Java Junior Meetup"))
        .when()
                .get();
        //@formatter:on
    }
}
