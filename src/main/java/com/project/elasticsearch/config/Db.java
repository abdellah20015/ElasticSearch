package com.project.elasticsearch.config;

import java.util.List;
import java.util.stream.Collectors;

import com.project.elasticsearch.constants.Services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.AggregateOptions;
import io.vertx.ext.mongo.BulkOperation;
import io.vertx.ext.mongo.MongoClient;

public class Db extends AbstractVerticle {

  private MongoClient mongoClient;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    mongoClient = Conf.createMongoClient(vertx);

    vertx.eventBus().consumer(Services.DB_INSERT, this::insertDocument);
    vertx.eventBus().consumer(Services.DB_REMOVE_DOCUMENT, this::deleteDocument);
    vertx.eventBus().consumer(Services.DB_AGGREGATE, this::aggregate);
    vertx.eventBus().consumer(Services.DB_FIND, this::find);
    vertx.eventBus().consumer(Services.DB_FIND_ONE, this::findOne);

    startPromise.complete();
  }

  public void insertDocument(Message<JsonObject> message) {
    JsonObject payload = message.body();
    String collection = payload.getString("collection");
    JsonObject document = payload.getJsonObject("document");

    if (document == null) {
      message.fail(400, "Document cannot be null");
      return;
    }

    mongoClient.insert(collection, document, res -> {
      if (res.succeeded()) {
        message.reply(new JsonObject().put("status", "success").put("id", res.result()));
      } else {
        message.fail(500, res.cause().getMessage());
      }
    });
  }

  private void deleteDocument(Message<JsonObject> message) {
    JsonObject payload = message.body();
    String collection = payload.getString("collection");
    String id = payload.getString("id");

    JsonObject query = new JsonObject().put("_id", id);


    mongoClient.findOne(collection, query, null, findRes -> {
        if (findRes.succeeded() && findRes.result() != null) {
            JsonObject documentToDelete = findRes.result();


            mongoClient.removeDocument(collection, query, deleteRes -> {
                JsonObject response = new JsonObject();

                if (deleteRes.succeeded() && deleteRes.result().getRemovedCount() > 0) {
                    response.put("status", "success")
                           .put("message", "Document supprimé avec succès")
                           .put("data", documentToDelete);
                } else {
                    response.put("status", "error")
                           .put("message", "Échec de la suppression")
                           .put("code", -1);
                }

                message.reply(response);
            });
        } else {
            JsonObject response = new JsonObject()
                .put("status", "error")
                .put("message", "Document non trouvé")
                .put("code", 404);
            message.reply(response);
        }
    });
  }



  private void aggregate(Message<JsonObject> message) {
    JsonObject payload = message.body();
    String collection = payload.getString("collection");
    JsonArray pipeline = payload.getJsonArray("pipeline");
    JsonObject options = payload.getJsonObject("options", new JsonObject());


    int page = options.getInteger("page", 1);
    int limit = options.getInteger("limit", 10);
    int skip = (page - 1) * limit;


    JsonObject sort = options.getJsonObject("sort", new JsonObject());
    boolean reverse = options.getBoolean("reverse", false);
    if (!sort.isEmpty()) {
        if (reverse) {
            sort.forEach(entry -> sort.put(entry.getKey(), entry.getValue().equals(1) ? -1 : 1));
        }
        pipeline.add(new JsonObject().put("$sort", sort));
    }


    pipeline.add(new JsonObject().put("$skip", skip));
    pipeline.add(new JsonObject().put("$limit", limit));

    JsonArray countPipeline = new JsonArray();
    for (Object stage : pipeline) {
        JsonObject stageObj = (JsonObject) stage;
        if (!stageObj.containsKey("$skip") && !stageObj.containsKey("$limit")) {
            countPipeline.add(stage);
        }
    }
    countPipeline.add(new JsonObject().put("$count", "total"));


    mongoClient
        .aggregateWithOptions(collection, countPipeline, new AggregateOptions(options))
        .collect(Collectors.toList())
        .onComplete(countRes -> {
            if (countRes.succeeded()) {
                mongoClient
                    .aggregateWithOptions(collection, pipeline, new AggregateOptions(options))
                    .collect(Collectors.toList())
                    .onComplete(dataRes -> {
                        JsonObject response = new JsonObject();

                        if (dataRes.succeeded()) {
                            long total = countRes.result().isEmpty() ? 0 : countRes.result().get(0).getLong("total", 0L);
                            int totalPages = (int) Math.ceil((double) total / limit);

                            response.put("status", "success")
                                   .put("pagination", new JsonObject()
                                       .put("total", total)
                                       .put("page", page)
                                       .put("limit", limit)
                                       .put("totalPages", totalPages))
                                   .put("data", new JsonArray(dataRes.result()));
                        } else {
                            response.put("status", "error")
                                   .put("message", dataRes.cause().getMessage())
                                   .put("code", 500);
                        }

                        message.reply(response);
                    });
            } else {
                JsonObject response = new JsonObject()
                    .put("status", "error")
                    .put("message", countRes.cause().getMessage())
                    .put("code", 500);

                message.reply(response);
            }
        });
}

private void find(Message<JsonObject> message) {
  JsonObject payload = message.body();
  String collection = payload.getString("collection");
  JsonObject query = payload.getJsonObject("query", new JsonObject());
  JsonObject fields = payload.getJsonObject("fields", null);

  mongoClient.find(collection, query, findRes -> {
      JsonObject response = new JsonObject();

      if (findRes.succeeded()) {
          response.put("status", "success")
                 .put("data", new JsonArray(findRes.result()));
      } else {
          response.put("status", "error")
                 .put("message", findRes.cause().getMessage())
                 .put("code", 500);
      }

      message.reply(response);
  });
}

private void findOne(Message<JsonObject> message) {
  JsonObject payload = message.body();
  String collection = payload.getString("collection");
  JsonObject query = payload.getJsonObject("query", new JsonObject());
  JsonObject fields = payload.getJsonObject("fields", null);

  mongoClient.findOne(collection, query, fields, findRes -> {
      JsonObject response = new JsonObject();

      if (findRes.succeeded()) {
          if (findRes.result() != null) {
              response.put("status", "success")
                     .put("data", findRes.result());
          } else {
              response.put("status", "error")
                     .put("message", "Document non trouvé")
                     .put("code", 404);
          }
      } else {
          response.put("status", "error")
                 .put("message", findRes.cause().getMessage())
                 .put("code", 500);
      }

      message.reply(response);
  });
}

}
