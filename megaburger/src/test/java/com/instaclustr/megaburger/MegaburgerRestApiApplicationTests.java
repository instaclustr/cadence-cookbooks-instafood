package com.instaclustr.megaburger;

import com.instaclustr.megaburger.controller.OrdersApi;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MegaburgerRestApiApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private OrdersApi ordersApi;

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @AfterEach
    public void tearDown() {
        ordersApi.deleteAll();
    }

    @Test
    void getOrdersShouldReturnEmptyList() {
        get("/orders").then()
                .assertThat()
                .statusCode(200)
                .body("$", empty());
    }

    @Test
    void givenNewOrderWhenPostingItShouldCreateWithId() {
        given()
                .header("content-type", "application/json")
                .body("{" +
                        "\"meal\": \"hamburger\"," +
                        "\"quantity\": 1" +
                        "}")
                .post("/orders").then()
                .assertThat()
                .statusCode(201)
                .body("id", is(notNullValue()));
    }

    @Test
    void givenASystemLoadedWithAnOrderGetOrdersShouldReturnIt() {
        given()
                .header("content-type", "application/json")
                .body("{" +
                        "\"meal\": \"hamburger\"," +
                        "\"quantity\": 1" +
                        "}")
                .post("/orders").then()
                .assertThat()
                .statusCode(201);

        get("/orders").then()
                .assertThat()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].meal", is("hamburger"))
                .body("[0].status", is("PENDING"))
                .body("[0].quantity", is(1));
    }

    @Test
    void givenASystemLoadedWithAnOrderGetOrderByIdShouldReturnIt() {
        Integer id = given()
                .header("content-type", "application/json")
                .body("{" +
                        "\"meal\": \"hamburger\"," +
                        "\"quantity\": 1" +
                        "}")
                .post("/orders").then()
                .assertThat()
                .statusCode(201)
                .extract()
                .body().path("id");

        get("/orders/" + id).then()
                .assertThat()
                .statusCode(200)
                .body("meal", is("hamburger"))
                .body("status", is("PENDING"))
                .body("quantity", is(1));
    }

    @Test
    void givenASystemLoadedWithAnOrderPatchOrderShouldUpdateIt() {
        Integer id = given()
                .header("content-type", "application/json")
                .body("{" +
                        "\"meal\": \"hamburger\"," +
                        "\"quantity\": 1" +
                        "}")
                .post("/orders").then()
                .assertThat()
                .statusCode(201)
                .extract()
                .body().path("id");

        given()
                .header("content-type", "application/json")
                .body("{" +
                        "\"status\": \"ACCEPTED\"," +
                        "\"eta_minutes\": 15" +
                        "}")
                .patch("/orders/" + id).then()
                .assertThat()
                .statusCode(200)
                .body("meal", is("hamburger"))
                .body("status", is("ACCEPTED"))
                .body("eta_minutes", is(15))
                .body("quantity", is(1));
    }
}