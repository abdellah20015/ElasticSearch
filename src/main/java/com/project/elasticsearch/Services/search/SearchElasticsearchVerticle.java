package com.project.elasticsearch.Services.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.project.elasticsearch.config.ElasticsearchConfig;
import com.project.elasticsearch.constants.Services;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Map;

public class SearchElasticsearchVerticle extends AbstractVerticle {
  private static final String INDEX_NAME = "elastic_search";
  private ElasticsearchClient esClient;

  @Override
  public void start(Promise<Void> startPromise) {
    esClient = ElasticsearchConfig.createClient();
    vertx.eventBus().consumer(Services.SEARCH_ELASTICSEARCH, this::handleSearch);
    startPromise.complete();
    System.out.println("SearchElasticsearchVerticle started successfully");
  }

  private void handleSearch(Message<Object> message) {
    JsonObject request = (JsonObject) message.body();

    try {
      String query = request.getString("query");
      Integer page = request.getInteger("page", 1);
      Integer size = request.getInteger("size", 10);

      if (query == null) {
        message.reply(new JsonObject()
          .put("status", "error")
          .put("message", "query is required"));
        return;
      }


      int from = (page - 1) * size;
      if (size > 100) size = 100;


      Integer finalSize = size;
      SearchResponse<Object> response = esClient.search(s -> s
          .index(INDEX_NAME)
          .query(q -> q
            .queryString(qs -> qs.query(query)))
          .from(from)
          .size(finalSize),
        Object.class
      );

      JsonArray results = new JsonArray();
      response.hits().hits().forEach(hit -> {
        results.add(new JsonObject((Map<String, Object>) hit.source()));
      });


      JsonObject reply = new JsonObject()
        .put("status", "success")
        .put("results", results)
        .put("total", response.hits().total().value())
        .put("count", results.size())
        .put("page", page)
        .put("size", size);

      message.reply(reply);

    } catch (Exception e) {
      message.reply(new JsonObject()
        .put("status", "error")
        .put("message", "Search failed: " + e.getMessage()));
    }
  }
}
