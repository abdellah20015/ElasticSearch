package com.project.elasticsearch;


import com.project.elasticsearch.Services.csv.CsvImportVerticle;
import com.project.elasticsearch.config.Conf;

import com.project.elasticsearch.config.Db;
import com.project.elasticsearch.constants.Services;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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

import java.util.List;


public class MainVerticle extends AbstractVerticle {

  private static final Integer PORT = 9999;


  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    String path = "src/main/api/openapi.json";
    Conf.createMongoClient(vertx);
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
      routerBuilder.getRoute("importCsvToElasticsearch").addHandler(this::handleCsvImport);


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

  private void handleCsvImport(RoutingContext ctx) {
    List<FileUpload> uploads = ctx.fileUploads();
    if (uploads.isEmpty()) {
      ctx.response()
        .setStatusCode(400)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("error", "No file uploaded").encode());
      return;
    }

    FileUpload csvFile = uploads.get(0);
    if (!csvFile.contentType().equals("text/csv") && !csvFile.fileName().endsWith(".csv")) {
      ctx.response()
        .setStatusCode(400)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("error", "File must be CSV format").encode());
      return;
    }

    JsonObject request = new JsonObject()
      .put("filePath", csvFile.uploadedFileName());

    System.out.println("Début du traitement CSV");

    vertx.eventBus().request(Services.CSV_PROCESS, request, ar -> {
      if (ar.succeeded()) {
        JsonObject result = (JsonObject) ar.result().body();
        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(result.encode());


        vertx.fileSystem().delete(csvFile.uploadedFileName(), deleteResult -> {
          if (deleteResult.failed()) {
            System.err.println("Erreur lors de la suppression du fichier temporaire: " +
              deleteResult.cause().getMessage());
          }
        });
      } else {
        ctx.response()
          .setStatusCode(500)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject()
            .put("error", "Erreur lors du traitement CSV: " + ar.cause().getMessage())
            .encode());
      }
    });
  }



  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
    vertx.deployVerticle(new CsvImportVerticle());
    vertx.deployVerticle(new Db());

  }


}
