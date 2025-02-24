package com.project.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.project.elasticsearch.Services.csv.CsvElasticsearchVerticle;


import com.project.elasticsearch.Services.search.SearchElasticsearchVerticle;
import com.project.elasticsearch.config.ElasticsearchConfig;
import com.project.elasticsearch.constants.Services;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.openapi.contract.OpenAPIContract;



public class MainVerticle extends AbstractVerticle {

  private static final Integer PORT = 9999;


  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    String path = "src/main/api/openapi.json";
    ElasticsearchClient esClient = ElasticsearchConfig.createClient();
    OpenAPIContract.from(vertx, path)
    .onSuccess(contract -> {
      // Create a router builder
      RouterBuilder routerBuilder = RouterBuilder.create(vertx, contract, RequestExtractor.withBodyHandler());
      // Create a session store
      LocalSessionStore sessionStore = LocalSessionStore.create(vertx);
      // Create a session handler
      SessionHandler sessionHandler = SessionHandler.create(sessionStore);

      // Mount the session handler
      routerBuilder.rootHandler(sessionHandler);

      // Mount the CORS handler
      routerBuilder.rootHandler(
        CorsHandler.create().addOrigin("*")
          .addOrigin("*")
          .allowedMethod(HttpMethod.GET)
          .allowedMethod(HttpMethod.POST)
          .allowedMethod(HttpMethod.OPTIONS)
          .allowedMethod(HttpMethod.PATCH)
          .allowedMethod(HttpMethod.PUT)
          .allowedMethod(HttpMethod.DELETE)
          .allowedHeader("Authorization")
          .allowedHeader("Content-Type")
          .allowCredentials(true)
      );


      // Mount the body handler
      routerBuilder.rootHandler(
       BodyHandler.create()
         .setUploadsDirectory("uploads")
         .setBodyLimit(500 * 2048 * 2048)
      );


      //CSV
      routerBuilder.getRoute("uploadCsvToElasticsearch").addHandler(this::handleRequest);

      //Search
      routerBuilder.getRoute("searchElasticsearch").addHandler(this::handleRequest);


      // Create a router
      Router router = routerBuilder.createRouter();

      //  create a static handler for the uploads directory
      router.route("/uploads/*").handler(StaticHandler.create("uploads"));

      //  create http server and listen on port 9999
      vertx.createHttpServer().requestHandler(router).listen(PORT)
      .onComplete(http -> {
        if (http.succeeded()) {
          startPromise.complete();
          System.out.println("HTTP server started on port " + PORT);
        } else {
          startPromise.fail(http.cause());
        }
      });
    })
    .onFailure(err -> {
      startPromise.fail(err);
      System.err.println("Failed to load OpenAPI contract: " + err.getMessage());
    });
  }

  private void handleRequest(RoutingContext ctx) {
    try {
      String operationId = ctx.currentRoute().getMetadata("operationId").toString();
      JsonObject responseBody;

      switch (operationId) {
        case "uploadCsvToElasticsearch":
          if (ctx.fileUploads().isEmpty()) {
            responseBody = new JsonObject()
              .put("status", "error")
              .put("message", "No file uploaded");
            ctx.response()
              .setStatusCode(400)
              .putHeader("Content-Type", "application/json")
              .end(responseBody.encode());
            return;
          }

          FileUpload upload = ctx.fileUploads().iterator().next();

          if (!upload.contentType().equals("text/csv") && !upload.fileName().endsWith(".csv")) {
            responseBody = new JsonObject()
              .put("status", "error")
              .put("message", "File must be CSV format");
            ctx.response()
              .setStatusCode(400)
              .putHeader("Content-Type", "application/json")
              .end(responseBody.encode());
            return;
          }

          String indexName = ctx.request().getFormAttribute("indexName");
          if (indexName == null || indexName.trim().isEmpty()) {
            responseBody = new JsonObject()
              .put("status", "error")
              .put("message", "Index name is required");
            ctx.response()
              .setStatusCode(400)
              .putHeader("Content-Type", "application/json")
              .end(responseBody.encode());
            return;
          }

          JsonObject message = new JsonObject()
            .put("filePath", upload.uploadedFileName())
            .put("fileName", upload.fileName())
            .put("indexName", indexName);

          vertx.eventBus().request(Services.CSV_TO_ELASTICSEARCH, message, new DeliveryOptions().setSendTimeout(2400000), reply -> {
            JsonObject eventBusResponse;
            if (reply.succeeded()) {
              eventBusResponse = (JsonObject) reply.result().body();
              ctx.response()
                .setStatusCode(eventBusResponse.getBoolean("success", false) ? 200 : 500)
                .putHeader("Content-Type", "application/json")
                .end(eventBusResponse.encode());
            } else {
              eventBusResponse = new JsonObject()
                .put("status", "error")
                .put("message", "Failed to process CSV file: " + reply.cause().getMessage());
              ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(eventBusResponse.encode());
            }
          });
          break;

        case "searchElasticsearch":
          JsonObject requestBody = ctx.body().asJsonObject();

          if (requestBody == null) {
            responseBody = new JsonObject()
              .put("status", "error")
              .put("message", "Request body is required");
            ctx.response()
              .setStatusCode(400)
              .putHeader("Content-Type", "application/json")
              .end(responseBody.encode());
            return;
          }

          vertx.eventBus().request(Services.SEARCH_ELASTICSEARCH, requestBody, reply -> {
            JsonObject eventBusResponse;
            if (reply.succeeded()) {
              eventBusResponse = (JsonObject) reply.result().body();
              ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(eventBusResponse.encode());
            } else {
              eventBusResponse = new JsonObject()
                .put("status", "error")
                .put("message", "Search failed: " + reply.cause().getMessage());
              ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(eventBusResponse.encode());
            }
          });
          break;

        default:
          responseBody = new JsonObject()
            .put("status", "error")
            .put("message", "Unknown operation");
          ctx.response()
            .setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end(responseBody.encode());
          break;
      }

    } catch (Exception e) {
      JsonObject errorResponse = new JsonObject()
        .put("status", "error")
        .put("message", "Internal server error: " + e.getMessage());
      ctx.response()
        .setStatusCode(500)
        .putHeader("Content-Type", "application/json")
        .end(errorResponse.encode());
    }
  }


  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
    vertx.deployVerticle(new CsvElasticsearchVerticle());
    vertx.deployVerticle(new SearchElasticsearchVerticle());

  }


}
