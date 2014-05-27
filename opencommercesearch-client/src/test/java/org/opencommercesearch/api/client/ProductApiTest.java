package org.opencommercesearch.api.client;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencommercesearch.api.client.request.ApiResponse;
import org.opencommercesearch.api.client.request.SearchRequest;
import org.opencommercesearch.api.client.response.SearchResponse;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.restlet.Client;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.representation.StreamRepresentation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author jmendez
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest(Client.class)
public class ProductApiTest {
  Client client = PowerMockito.mock(Client.class);

  @Before
  public void setup() throws Exception {
  }

  @Test
  public void testLoadProperties() {
    //TODO Implement this
  }

  @Test
  public void testHandle() throws Exception {
    ProductApi productApi = new ProductApi();
    productApi.setClient(client);

    Response clientResponse = new Response(new Request());
    clientResponse.setEntity(new TestStreamRepresentation("{\"message\":\"test response\"}"));
    when(client.handle(any(Request.class))).thenReturn(clientResponse);

    TestRequest testRequest = new TestRequest();
    TestResponse response = (TestResponse) productApi.handle(testRequest, TestResponse.class);

    assertEquals("Got a search response", "test response", response.getMessage());
  }

  @Test
  public void testHandleWithInvalidJson() throws Exception {
    ProductApi productApi = new ProductApi();
    productApi.setClient(client);
    Response clientResponse = new Response(new Request());
    clientResponse.setEntity(new TestStreamRepresentation("{\"unknownFieldName\":\"test response\"}"));
    when(client.handle(any(Request.class))).thenReturn(clientResponse);

    TestRequest testRequest = new TestRequest();
    try {
      productApi.handle(testRequest, TestResponse.class);
    }
    catch(Exception e) {
      if(e instanceof ProductApiException) {
        //The exception must have details.
        Throwable cause = e.getCause();
        assertTrue("Got a JSON exception.", cause instanceof UnrecognizedPropertyException);
        //Must not have chained exceptions. A JSON mapping error causes the API to return immediately.
        assertNull("There are no chained exceptions", cause.getCause());
      }
    }
  }

  @Test
  public void testHandleWith404And400() throws Exception {
    //404 and 400 should not cause retries.
    ProductApi productApi = new ProductApi();
    productApi.setClient(client);
    Response clientResponse = new Response(new Request());
    clientResponse.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
    when(client.handle(any(Request.class))).thenReturn(clientResponse).thenReturn(clientResponse);

    TestRequest testRequest = new TestRequest();
    try {
      productApi.handle(testRequest, TestResponse.class);
    }
    catch(Exception e) {
      assertTrue("Got the correct exception type.", e instanceof ProductApiException);
      assertTrue("Thrown exception due 404.", e.getMessage().contains("404"));
      //Must not have chained exceptions. A JSON mapping error causes the API to return immediately.
      assertNull("There are no chained exceptions", e.getCause());
    }

    clientResponse.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);

    try {
      productApi.handle(testRequest, TestResponse.class);
    }
    catch(Exception e) {
      assertTrue("Got the correct exception type.", e instanceof ProductApiException);
      assertTrue("Thrown exception due 400.", e.getMessage().contains("400"));
      //Must not have chained exceptions. A JSON mapping error causes the API to return immediately.
      assertNull("There are no chained exceptions", e.getCause());
    }
  }

  @Test
  public void testHandleWithRetry() throws Exception {
    ProductApi productApi = new ProductApi();
    productApi.setClient(client);
    productApi.setMaxRetries(2);

    Response response500 = new Response(new Request());
    Response response1001 = new Response(new Request());
    Response response1000 = new Response(new Request());
    response500.setStatus(Status.SERVER_ERROR_INTERNAL);
    response1001.setStatus(Status.CONNECTOR_ERROR_COMMUNICATION);
    response1000.setStatus(Status.CONNECTOR_ERROR_CONNECTION);

    when(client.handle(any(Request.class))).thenReturn(response500).thenReturn(response1001).thenReturn(response1000);

    TestRequest testRequest = new TestRequest();
    try {
      productApi.handle(testRequest, TestResponse.class);
    }
    catch(Exception e) {
      assertTrue("Got the correct exception type.", e instanceof ProductApiException);
      assertTrue("Thrown exception after many retires.", e.getMessage().contains("Failed to handle API request"));
      //Must have chained exceptions. A JSON mapping error causes the API to return immediately.
      assertNotNull("There are chained exceptions", e.getCause());
      Throwable cause1 = e.getCause();
      assertTrue("Cause 1 is 1000", cause1 instanceof ProductApiException && cause1.getMessage().contains("1000"));
      assertNotNull(cause1.getCause());
      Throwable cause2 = cause1.getCause();

      assertTrue("Cause 2 is 1001", cause2 instanceof ProductApiException && cause2.getMessage().contains("1001"));
      assertNotNull(cause2.getCause());
      Throwable cause3 = cause2.getCause();
      assertTrue("Cause 3 is 500", cause3 instanceof ProductApiException && cause3.getMessage().contains("500"));
      assertNull("No more than 3 retries", cause3.getCause());
    }
  }

  @Test
  public void testGetUrl() throws IOException {
    ProductApi productApi = new ProductApi();
    String url = productApi.getRequestUrl(new TestRequest());

    assertEquals("http://localhost:9000/v1/dummy/endpoint?preview=false", url);

    productApi.setVersion("v2");
    url = productApi.getRequestUrl(new TestRequest());
    assertEquals("http://localhost:9000/v2/dummy/endpoint?preview=false", url);

    productApi.setPreview(true);
    url = productApi.getRequestUrl(new TestRequest());
    assertEquals("http://localhost:9000/v2/dummy/endpoint?preview=true", url);

    productApi.setHost("myapiserver:8999");
    url = productApi.getRequestUrl(new TestRequest());
    assertEquals("http://myapiserver:8999/v2/dummy/endpoint?preview=true", url);
  }

  @Test
  public void testSearch() throws IOException, ProductApiException {
    ProductApi productApi = new ProductApi();
    productApi.setPreview(true);

    productApi.start();
    SearchRequest searchRequest = new SearchRequest();
    searchRequest.setQuery("jackets");
    searchRequest.setSite("bcs");
    SearchResponse response = productApi.searchProducts(searchRequest);



    productApi.stop();
  }

  class TestRequest extends ApiRequest {

    @Override
    protected Request asRestletRequest(String url) {
      return new Request(Method.GET, url);
    }

    @Override
    protected String getEndpoint() {
      return "/dummy/endpoint";
    }
  }

  class TestStreamRepresentation extends StreamRepresentation {

    private String content = StringUtils.EMPTY;
    /**
     * Creates a new TestStreamRepresentation.
     */
    public TestStreamRepresentation(String content) {
      super(MediaType.APPLICATION_JSON);
      this.content = content;
    }

    @Override
    public InputStream getStream() throws IOException {
      return new ByteArrayInputStream(content.getBytes());
    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
}

class TestResponse extends ApiResponse {
  private String message;

  public String getMessage() {
    return message;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setMessage(String message) {
    this.message = message;
  }
}

