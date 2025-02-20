package com.project.elasticsearch.Services.csv;

import com.project.elasticsearch.constants.Services;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class CsvImportVerticle extends AbstractVerticle {
  private static final Pattern CSV_PATTERN = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
  private static final String DEFAULT_COLLECTION = "data_elastic_search";

  @Override
  public void start(Promise<Void> startPromise) {
    vertx.eventBus().consumer(Services.CSV_PROCESS, message -> {
      JsonObject request = (JsonObject) message.body();
      String filePath = request.getString("filePath");
      String collection = DEFAULT_COLLECTION;

      Instant startTime = Instant.now();

      processCsvFile(filePath, collection)
        .onSuccess(result -> {
          Instant endTime = Instant.now();
          Duration duration = Duration.between(startTime, endTime);
          JsonObject response = new JsonObject()
            .put("status", "success")
            .put("rowsInserted", result.getInteger("totalProcessed", 0))
            .put("timeTaken", duration.toMillis() + " ms");
          message.reply(response);
        })
        .onFailure(err -> message.fail(500, err.getMessage()));
    });
  }

  private Future<JsonObject> processCsvFile(String filePath, String collection) {
    Promise<JsonObject> promise = Promise.promise();
    AtomicInteger totalProcessed = new AtomicInteger(0);

    vertx.fileSystem().readFile(filePath)
      .onSuccess(buffer -> {
        String[] lines = buffer.toString().split("\n");
        if (lines.length == 0) {
          promise.fail("Empty file");
          return;
        }

        String headerLine = lines[0].trim();
        String[] headers = CSV_PATTERN.split(headerLine);
        processLine(lines, 1, headers, collection, totalProcessed, promise);
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  private void processLine(String[] lines, int currentIndex, String[] headers,
                           String collection, AtomicInteger totalProcessed,
                           Promise<JsonObject> promise) {
    if (currentIndex >= lines.length) {
      promise.complete(new JsonObject()
        .put("totalProcessed", totalProcessed.get())
        .put("status", "success"));
      return;
    }

    String line = lines[currentIndex];
    if (line.trim().isEmpty()) {
      processLine(lines, currentIndex + 1, headers, collection, totalProcessed, promise);
      return;
    }

    try {
      JsonObject newDocument = createDocument(line, headers);
      if (newDocument == null) {
        System.err.printf("Format de document invalide à la ligne %d%n", currentIndex);
        processLine(lines, currentIndex + 1, headers, collection, totalProcessed, promise);
        return;
      }

      JsonObject insertRequest = new JsonObject()
        .put("collection", collection)
        .put("document", newDocument);

      vertx.eventBus().request(Services.DB_INSERT, insertRequest, insertReply -> {
        if (insertReply.succeeded()) {
          totalProcessed.incrementAndGet();
          System.out.printf("Ligne %d : Document inséré avec succès%n", currentIndex);
        } else {
          System.err.printf("Erreur d'insertion ligne %d : %s%n",
            currentIndex, insertReply.cause().getMessage());
        }
        processLine(lines, currentIndex + 1, headers, collection, totalProcessed, promise);
      });
    } catch (Exception e) {
      System.err.printf("Erreur de traitement ligne %d : %s%n", currentIndex, e.getMessage());
      processLine(lines, currentIndex + 1, headers, collection, totalProcessed, promise);
    }
  }

  private JsonObject createDocument(String line, String[] headers) {
    String[] values = CSV_PATTERN.split(line.trim());
    if (values.length != headers.length) return null;

    JsonObject document = new JsonObject();
    for (int i = 0; i < headers.length; i++) {
      String value = cleanValue(values[i]);
      String header = cleanValue(headers[i]);

      if (value.matches("^\\d+$")) {
        document.put(header, Long.valueOf(value));
      } else if (value.matches("^\\d*\\.\\d+$")) {
        document.put(header, Double.valueOf(value));
      } else {
        document.put(header, value);
      }
    }
    return document;
  }

  private String cleanValue(String value) {
    value = value.replaceAll("^\"|\"$", "").trim();
    value = value.replaceAll("[\\\\;{}()\\[\\]\"']", "");
    return value;
  }
}
