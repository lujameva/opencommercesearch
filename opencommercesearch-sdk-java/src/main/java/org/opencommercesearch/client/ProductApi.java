package org.opencommercesearch.client;

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.client.request.*;
import org.opencommercesearch.client.request.Request;
import org.opencommercesearch.client.response.BrowseResponse;
import org.opencommercesearch.client.response.Response;
import org.opencommercesearch.client.response.SearchResponse;
import org.restlet.*;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Client class that handles requests to the product API.
 * <p/>
 * This class is not thread safe.
 *
 * @author jmendez
 */
public class ProductApi {
  private static Logger logger = LoggerFactory.getLogger(ProductApi.class);

  private final static Set<Status> OK_STATUS_SET = new HashSet<Status>();

  static {
    OK_STATUS_SET.add(Status.SUCCESS_OK);
    OK_STATUS_SET.add(Status.SUCCESS_CREATED);
  }

  /**
   * Supported request methods by the product API client.
   */
  public enum RequestMethod {
    GET, PUT, POST, DELETE
  }

  /**
   * The max number of times to attempt a request before throwing an exception.
   */
  private int maxRetries = 2;

  /**
   * The API host name and the port
   */
  private String host = "localhost:9000";


  /**
   * The API version that should be used.
   */
  private String version = "v1";

  /**
   * The base URL
   */
  private String baseUrl = "http://" + host + "/" + version;

  /**
   * Whether or not this client should retrieve preview data.
   */
  private Boolean preview = false;

  /**
   * Configuration properties for the product API. For example HTTP client properties.
   */
  private Properties configProperties = new Properties();

  /**
   * The restlet client used to send request to the product API.
   */
  private Client client;

  /**
   * Creates a new ProductApi instance with properties existing in the class path. If no properties are found, an exception is thrown.
   *
   * @throws IOException if no properties are found in the class path.
   */
  public ProductApi() throws IOException {
    this.configProperties = loadProperties();

    initProperties();
  }

  /**
   * Creates a new ProductApi instance with the given properties.
   *
   * @param properties Map of configuration properties understood by the ApiClient.
   */
  public ProductApi(Properties properties) {
    this.configProperties = properties;

    initProperties();
  }

  /**
   * Initializes some default properties from the Product API client
   */
  protected void initProperties() {
    if (configProperties.getProperty("host") != null) {
      setHost(configProperties.getProperty("host"));
    }

    if (configProperties.getProperty("version") != null) {
      setVersion(configProperties.getProperty("version"));
    }

    if (configProperties.getProperty("preview") != null) {
      setPreview(Boolean.valueOf(configProperties.getProperty("preview")));
    }

    if (configProperties.getProperty("maxRetries") != null) {
      setMaxRetries(Integer.parseInt(configProperties.getProperty("maxRetries")));
    }
  }

  /**
   * Performs a search for products.
   *
   * @param request A search request.
   * @return A search response with products matching the given search request.
   * @throws ProductApiException If there are underlying HTTP communication issues, if the API responded with errors or if there response parsing problems.
   */
  public SearchResponse search(SearchRequest request) throws ProductApiException {
    return (SearchResponse) handle(request, SearchResponse.class);
  }

  /**
   * Browse products by brand categories.
   *
   * @param request A brand category browse request.
   * @return A browse response with products matching the given browse request.
   * @throws ProductApiException If there are underlying HTTP communication issues, if the API responded with errors or if there response parsing problems.
   */
  public BrowseResponse browse(BrowseBrandCategoryRequest request) throws ProductApiException {
    return (BrowseResponse) handle(request, BrowseResponse.class);
  }

  /**
   * Browse products by brand.
   *
   * @param request A brand browse request.
   * @return A browse response with products matching the given browse request.
   * @throws ProductApiException If there are underlying HTTP communication issues, if the API responded with errors or if there response parsing problems.
   */
  public BrowseResponse browseByBrand(BrowseBrandRequest request) throws ProductApiException {
    return (BrowseResponse) handle(request, BrowseResponse.class);
  }

  /**
   * Browse products by category.
   *
   * @param request A category browse request.
   * @return A browse response with products matching the given browse request.
   * @throws ProductApiException If there are underlying HTTP communication issues, if the API responded with errors or if there response parsing problems.
   */
  public BrowseResponse browseByCategory(BrowseCategoryRequest request) throws ProductApiException {
    return (BrowseResponse) handle(request, BrowseResponse.class);
  }

  /**
   * Receives an API request, translates it into a valid HTTP request which is sent to the product API server and then processes the result by unmarshalling any
   * data into a valid ApiResponse subclass.
   *
   * @param request The API request to be handled.
   * @param clazz   The API response class that should be returned.
   * @param <T>     The API response type that will be returned.
   * @return A API response with data returned from the product API server.
   * @throws ProductApiException If there are underlying HTTP communication issues, if the API responded with errors or if there response parsing problems.
   */
  protected <T extends Response> Response handle(DefaultRequest request, Class<T> clazz) throws ProductApiException {
    int retries = 0;
    Exception lastException = null;

    do {
      org.restlet.Request restletRequest = null;
      org.restlet.Response response = null;

      try {
        String url = getRequestUrl(request);
        if (url == null) {
          throw new IllegalArgumentException("Request " + request.getClass() + " has a null endpoint");
        }

        restletRequest = convertToRestlet(url, request);

        if (logger.isDebugEnabled()) {
          logger.debug("Sending API request with base URL: " + restletRequest.getResourceRef());
        }

        response = client.handle(restletRequest);

        if (logger.isDebugEnabled()) {
          logger.debug("API response is '" + response.getStatus() + "'");
        }

        if (OK_STATUS_SET.contains(response.getStatus())) {
          return unmarshall(response, clazz);
        }

        //Handle special errors
        if (Status.CLIENT_ERROR_NOT_FOUND.equals(response.getStatus()) || Status.CLIENT_ERROR_BAD_REQUEST.equals(response.getStatus())) {
          //If 404 is returned, do not retry.
          throw new ProductApiException("Failed to handle request as API server returned " + response.getStatus() + ". Check that version '" + getVersion() +
              "', endpoint '" + request.getEndpoint() + "' and parameters '" + request.getQueryString() + "' are correct.");
        } else {
          //If anything else, retry.
          //If 500 is returned, retry as it may be caused by a timeout talking to the backend services.
          //TODO: API should return 503 if it failed to contact any backend services (such as DB or Search Engine).
          lastException = new ProductApiException("API server returned " + response.getStatus(), lastException);
        }
      } catch (JsonProcessingException e) {
        throw new ProductApiException("Found invalid JSON format in the response.", e);
      } catch (IOException e) {
        if (logger.isDebugEnabled()) {
          logger.debug("Failed to handle API request, attempt " + retries);
        }

        e.initCause(lastException);
        lastException = e;
      }
      finally {
          if (response != null) {
              response.release();
          }
          if (restletRequest != null) {
              restletRequest.release();
          }
      }
    } while (retries++ < maxRetries);

    throw new ProductApiException("Failed to handle API request.", lastException);
  }

  /**
   * Starts the underlying HTTP connection.
   *
   * @throws ProductApiException If the underlying HTTP connection can't initialize.
   */
  public void start() throws ProductApiException {

    if (client != null) {
      stop();
    }

    Context context = new Context();

    for (Map.Entry<Object, Object> entry : configProperties.entrySet()) {
      context.getParameters().add(entry.getKey().toString(), entry.getValue().toString());
    }

    client = new Client(context, Protocol.HTTP);

    try {
      client.start();
      logger.info("Started product API client");
    } catch (Exception e) {
      throw new ProductApiException("Failed to initialize underlying HTTP client.", e);
    }
  }

  /**
   * Stops the underlying HTTP connection and releases any allocated resources.
   *
   * @throws ProductApiException If the underlying HTTP connection can't be stopped.
   */
  public void stop() throws ProductApiException {
    if (client != null) {
      try {
        client.stop();
        logger.info("Stopped product API client");
      } catch (Exception e) {
        throw new ProductApiException("Failed to stop underlying HTTP client.", e);
      } finally {
        client = null;
      }
    }
  }

  /**
   * Converts a request to a valid HTTP Restlet request.
   * <p/>
   * The returned Restlet request may vary depending on the input request method.
   *
   * @param url     The URL for the HTTP request.
   * @param request The actual parameters to be included in the Restlet request.
   * @return A new Restlet request with the given URL and parameters set.
   * @throws ProductApiException 
   */
  org.restlet.Request convertToRestlet(String url, Request request) throws ProductApiException {
    switch (request.getMethod()) {
      case GET:
        return new org.restlet.Request(Method.GET, url + "&" + request.getQueryString());
    }

    throw new IllegalArgumentException("Method " + request.getMethod() + " not supported.");
  }

  /**
   * Reads a plain HTTP response and transforms it into a user friendly ApiResponse according to its type.
   *
   * @param response The HTTP response.
   * @return An API response that matches the given HTTP data.
   */
  private <T extends Response> Response unmarshall(org.restlet.Response response, Class<T> clazz) throws IOException {
    ObjectMapper mapper = new ObjectMapper(); //TODO: should this be stored?
    return mapper.readValue(response.getEntity().getStream(), clazz);
  }

  /**
   * Get the proper URL to use based on the request endpoint and current API settings.
   * <p/>
   * <p/>
   * An example URL will look like: http://localhost:9000/v1/products?preview=false
   *
   * @param request Request used to calculate the URL from.
   * @return A properly formed request URL for the API.
   */
  protected String getRequestUrl(DefaultRequest request) {
    String endpoint = request.getEndpoint();
    if (StringUtils.isNotEmpty(endpoint)) {
      StringBuilder url = new StringBuilder();
      url.append(baseUrl);

      if (endpoint.charAt(0) != '/') {
        url.append("/");
      }

      url.append(endpoint).append("?preview=").append(getPreview());
      return url.toString();
    } else {
      return null;
    }
  }

  /**
   * Loads configuration properties defined in a file "config.properties" on the class path.
   *
   * @return A properties instance with any configuration options that are needed.
   * @throws IOException If the file config.properties can't be read.
   */
  protected Properties loadProperties() throws IOException {
    Properties properties = new Properties();
    InputStream in = ClassLoader.getSystemResourceAsStream("config.properties");

    if (in == null) {
      throw new IOException("File config.properties not found on classpath.");
    }

    properties.load(in);
    in.close();
    return properties;
  }

  protected Logger getLogger() {
    return ProductApi.logger;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
    setBaseUrl();
  }

  private void setBaseUrl() {
    this.baseUrl = "http://" + host + "/" + version;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
    setBaseUrl();
  }

  public Boolean getPreview() {
    return preview;
  }

  public void setPreview(Boolean preview) {
    this.preview = preview;
  }

  public Properties getConfigProperties() {
    return configProperties;
  }

  public void setConfigProperties(Properties configProperties) {
    this.configProperties = configProperties;
  }

  protected Client getClient() {
    return client;
  }

  protected void setClient(Client client) {
    this.client = client;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }
}
