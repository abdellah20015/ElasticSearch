package com.project.elasticsearch.Services.csv;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import com.project.elasticsearch.constants.Services;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.file.FileSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.project.elasticsearch.config.ElasticsearchConfig;

public class CsvElasticsearchVerticle extends AbstractVerticle {
  private ElasticsearchClient esClient;

  @Override
  public void start(Promise<Void> startPromise) {
    System.out.println("Starting CsvElasticsearchVerticle...");
    esClient = ElasticsearchConfig.createClient();
    vertx.eventBus().consumer(Services.CSV_TO_ELASTICSEARCH, this::processCsvFile);
    startPromise.complete();
    System.out.println("CsvElasticsearchVerticle started successfully");
  }

  private void processCsvFile(Message<Object> message) {
    System.out.println("Processing CSV file...");
    JsonObject request = (JsonObject) message.body();
    System.out.println("Request received: " + request.encode());

    String filePath = request.getString("filePath");
    String indexName = request.getString("indexName").toLowerCase();

    try {
      boolean indexExists = esClient.indices().exists(e -> e.index(indexName)).value();

      if (!indexExists) {
        System.out.println("Index does not exist. Creating new index: " + indexName);
        var createResponse = esClient.indices().create(c -> c
          .index(indexName)
          .mappings(m -> m.dynamic(DynamicMapping.True))
          .settings(s -> s.numberOfShards("1").numberOfReplicas("1"))
        );

        if (!createResponse.acknowledged()) {
          throw new Exception("Index creation not acknowledged");
        }
        System.out.println("Index created successfully: " + indexName);
      }

      processFile(filePath, indexName, message);

    } catch (Exception e) {
      System.err.println("Error in processCsvFile: " + e.getMessage());
      e.printStackTrace();
      message.reply(new JsonObject()
        .put("success", false)
        .put("status", "error")
        .put("message", "Failed to process CSV file: " + e.getMessage()));
    }
  }

  private void processFile(String filePath, String indexName, Message<Object> message) {
    AtomicInteger processedRows = new AtomicInteger(0);
    List<String> headers = new ArrayList<>();
    FileSystem fs = vertx.fileSystem();

    fs.readFile(filePath, readResult -> {
      if (readResult.succeeded()) {
        try {
          String fileContent = readResult.result().toString();
          String[] lines = fileContent.split("\n");

          System.out.println("Total lines in file: " + lines.length);

          if (lines.length > 0) {
            headers.addAll(List.of(lines[0].trim().split(",")));
            System.out.println("Headers detected: " + String.join(", ", headers));
          }

          for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
              Map<String, Object> document = createDocument(line, headers);
              int currentRow = processedRows.incrementAndGet();
              // Insertion directe dans Elasticsearch
              esClient.index(idx -> idx
                .index(indexName)
                .document(document)
              );
              System.out.println("Row " + currentRow + "/" + (lines.length - 1) + " inserted directly");
            }
          }

          fs.delete(filePath, deleteResult -> {
            if (deleteResult.succeeded()) {
              System.out.println("Successfully deleted CSV file: " + filePath);
            } else {
              System.err.println("Failed to delete CSV file: " + filePath + " - " + deleteResult.cause().getMessage());
            }
          });

          JsonObject response = new JsonObject()
            .put("success", true)
            .put("status", "success")
            .put("message", "CSV file processed successfully")
            .put("processedRows", processedRows.get());

          System.out.println("Sending final response to client: " + response.encode());
          message.reply(response);

          System.out.println("Final summary: Processed " + processedRows.get() + " rows successfully");

        } catch (Exception e) {
          System.err.println("Error processing file content: " + e.getMessage());
          e.printStackTrace();
          message.reply(new JsonObject()
            .put("success", false)
            .put("status", "error")
            .put("message", "Error processing CSV: " + e.getMessage()));
        }
      } else {
        System.err.println("Failed to read file: " + readResult.cause().getMessage());
        message.reply(new JsonObject()
          .put("success", false)
          .put("status", "error")
          .put("message", "Failed to read file: " + readResult.cause().getMessage()));
      }
    });
  }

  private Map<String, Object> createDocument(String line, List<String> headers) {
    Map<String, Object> document = new HashMap<>();
    String[] values = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");

    for (int i = 0; i < headers.size() && i < values.length; i++) {
      String value = values[i].trim().replaceAll("^\"|\"$", "");
      String header = headers.get(i).trim();

      try {
        if (value.matches("^\\d+$")) {
          document.put(header, Long.parseLong(value));
        } else if (value.matches("^\\d*\\.\\d+$")) {
          document.put(header, Double.parseDouble(value));
        } else {
          document.put(header, value);
        }
      } catch (NumberFormatException e) {
        document.put(header, value);
      }
    }
    return document;
  }
}
