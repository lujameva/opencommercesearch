package org.opencommercesearch.api.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.opencommercesearch.api.client.request.ApiResponse;
import org.opencommercesearch.api.client.request.SearchRequest;
import org.opencommercesearch.api.client.response.SearchResponse;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Protocol;
import org.restlet.data.Status;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Client class that handles request to the product API.
 * <p/>
 * This class is not thread safe.
 * @author jmendez
 */
public class ProductApi {

  private static Logger logger = LogManager.getLogger(ProductApi.class);

  private final static Set<Status> OK_STATUS_SET = new HashSet<Status>();

  static {
    OK_STATUS_SET.add(Status.SUCCESS_OK);
    OK_STATUS_SET.add(Status.SUCCESS_CREATED);
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
   * The base URL
   */
  private String baseUrl = "http://localhost:9000/v1";

  /**
   * The API version that should be used.
   */
  private String version = "v1";

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
   * @throws IOException if no properties are found in the class path.
   */
  public ProductApi() throws IOException {
    this.configProperties = loadProperties();

    if(configProperties.getProperty("host") != null) {
      setHost(configProperties.getProperty("host"));
    }
  }

  /**
   * Creates a new ProductApi instance with the given properties.
   * @param properties Map of configuration properties understood by the ApiClient.
   */
  public ProductApi(Properties properties) {
    this.configProperties = properties;

    if(configProperties.getProperty("host") != null) {
      setHost(configProperties.getProperty("host"));
    }

    if(configProperties.getProperty("version") != null) {
      setVersion(configProperties.getProperty("version"));
    }

    if(configProperties.getProperty("preview") != null) {
      setPreview(Boolean.valueOf(configProperties.getProperty("preview")));
    }

    if(configProperties.getProperty("maxRetires") != null) {
      setMaxRetries(Integer.parseInt(configProperties.getProperty("maxRetries")));
    }
  }

  /**
   * Performs a search for products.
   * @param request A search request.
   * @return A search response with products matching the given search request.
   * @throws ProductApiException If there are underlying HTTP communication issues, if the API responded with errors or if there response parsing problems.
   */
  public SearchResponse searchProducts(SearchRequest request) throws ProductApiException {
    return (SearchResponse) handle(request, SearchResponse.class);
  }

  /**
   * Receives an API request, translates it into a valid HTTP request which is sent to the product API server and then processes the result by unmarshalling any
   * data into a valid ApiResponse subclass.
   * @param request The API request to be handled.
   * @param clazz The API response class that should be returned.
   * @param <T> The API response type that will be returned.
   * @return A API response with data returned from the product API server.
   * @throws ProductApiException If there are underlying HTTP communication issues, if the API responded with errors or if there response parsing problems.
   */
  public <T extends ApiResponse> ApiResponse handle(ApiRequest request, Class<T> clazz) throws ProductApiException {
    int retries = 0;
    Exception lastException = null;

    do {
      try {
        String url = getRequestUrl(request);
        if(url == null) {
          throw new IllegalArgumentException("Request " + request.getClass() + " has a null endpoint");
        }

        Request restletRequest = request.asRestletRequest(url);

        if(logger.isDebugEnabled()) {
          logger.debug("Sending API request with base URL: " + restletRequest.getResourceRef());
        }

        Response response = client.handle(restletRequest);

        if(logger.isDebugEnabled()) {
          logger.debug("API response is '" + response.getStatus() + "'");
        }

        if(OK_STATUS_SET.contains(response.getStatus())) {
          return unmarshall(response, clazz);
        }

        //Handle special errors
        if(Status.CLIENT_ERROR_NOT_FOUND.equals(response.getStatus()) || Status.CLIENT_ERROR_BAD_REQUEST.equals(response.getStatus())) {
          //If 404 is returned, do not retry.
          throw new ProductApiException("Failed to handle request as API server returned " + response.getStatus() + ". Check that version '" + getVersion() +
              "', endpoint '" + request.getEndpoint() + "' and parameters '" + request.getQueryString() + "' are correct.");
        }
        else {
          //If anything else, retry.
          //If 500 is returned, retry as it may be caused by a timeout talking to the backend services.
          //TODO: API should return 503 if it failed to contact any backend services (such as DB or Search Engine).
          lastException = new ProductApiException("API server returned " + response.getStatus(), lastException);
        }
      }
      catch(JsonProcessingException e) {
        throw new ProductApiException("Found invalid JSON format in the response.", e);
      }
      catch(IOException e) {
        if(logger.isDebugEnabled()) {
          logger.debug("Failed to handle API request, attempt " + retries);
        }

        e.initCause(lastException);
        lastException = e;
      }
    } while (retries++ < maxRetries);

    throw new ProductApiException("Failed to handle API request.", lastException);
  }

  /**
   * Starts the underlying HTTP connection.
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
    } catch (Exception e) {
      throw new ProductApiException("Failed to initialize underlying HTTP client.", e);
    }
  }

  /**
   * Stops the underlying HTTP connection and releases any allocated resources.
   * @throws ProductApiException If the underlying HTTP connection can't be stopped.
   */
  public void stop() throws ProductApiException {
    if (client != null) {
      try {
        client.stop();
      }
      catch (Exception e) {
        throw new ProductApiException("Failed to stop underlying HTTP client.", e);
      }
      finally {
        client = null;
      }
    }
  }

  /**
   * Reads a plain HTTP response and transforms it into a user friendly ApiResponse according to its type.
   * @param response The HTTP response.
   * @return An API response that matches the given HTTP data.
   */
  private <T extends ApiResponse> ApiResponse unmarshall(Response response, Class<T> clazz) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(response.getEntity().getStream(), clazz);
  }

  /**
   * Get the proper URL to use based on the request endpoint and current API settings.
   * <p/>
   * <p/>
   * An example URL will look like: http://localhost:9000/v1/products?preview=false
   * @param request Request used to calculate the URL from.
   * @return A properly formed request URL for the API.
   */
  protected String getRequestUrl(ApiRequest request) {
    String endpoint = request.getEndpoint();
    if(StringUtils.isNotEmpty(endpoint)) {
      StringBuilder url = new StringBuilder();
      url.append(baseUrl);

      if (endpoint.charAt(0) != '/') {
        url.append("/");
      }

      url.append(endpoint).append("?preview=").append(getPreview());
      return url.toString();
    }
    else {
      return null;
    }
  }

  /**
   * Loads configuration properties defined in a file "config.properties" on the class path.
   * @return A properties instance with any configuration options that are needed.
   * @throws IOException If the file config.properties can't be read.
   */
  private Properties loadProperties() throws IOException {
    Properties properties = new Properties();
    InputStream in = this.getClass().getResourceAsStream("config.properties");

    if(in == null) {
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
