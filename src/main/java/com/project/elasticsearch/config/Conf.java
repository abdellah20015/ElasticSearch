package com.project.elasticsearch.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class Conf {
  private static final String CONNECTION_STRING = "mongodb+srv://abdellahagnaou92:Abdo2001@cluster0.3tow4.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";
  private static final String DATABASE_NAME = "elastic_search";

  public static MongoClient createMongoClient(Vertx vertx) {
    JsonObject config = new JsonObject()
      .put("connection_string", CONNECTION_STRING)
      .put("db_name", DATABASE_NAME);
    return MongoClient.createShared(vertx, config);
  }
}
