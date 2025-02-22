package com.project.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.elasticsearch.client.RestClient;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class ElasticsearchConfig {
  private static final String ES_HOST = "localhost";
  private static final int ES_PORT = 9200;
  private static final String ES_SCHEME = "http";
  private static final String ES_USERNAME = "elastic";
  private static final String ES_PASSWORD = "ABD565";

  public static ElasticsearchClient createClient() {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[]{new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() { return null; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
      }}, new java.security.SecureRandom());


      final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(AuthScope.ANY,
        new UsernamePasswordCredentials(ES_USERNAME, ES_PASSWORD));


      RestClient restClient = RestClient.builder(
          new HttpHost(ES_HOST, ES_PORT, ES_SCHEME))
        .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
          .setSSLContext(sslContext)
          .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
          .setDefaultCredentialsProvider(credentialsProvider))
        .build();

      ElasticsearchTransport transport = new RestClientTransport(
        restClient,
        new JacksonJsonpMapper()
      );

      return new ElasticsearchClient(transport);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create Elasticsearch client", e);
    }
  }
}
