package org.opencommercesearch.api.client;

import org.junit.Test;
import org.opencommercesearch.api.client.request.SearchRequest;
import org.restlet.Request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author jmendez
 */
public class ApiRequestTest {

  @Test
  public void testGetHttpRequest() {
    ApiRequest ar = new ApiRequest() {

      @Override
      protected Request asRestletRequest(String url) {
        return null;
      }

      @Override
      protected String getEndpoint() {
        return null;
      }
    };

    ar.addParam("param1", "v11");
    ar.addParam("param2", "v21");
    ar.addParam("param1", "v12");

    String queryString = ar.getQueryString();
    assertEquals("param1=v11,v12&param2=v21", queryString);

    ar.setParam("param1", "v13");
    queryString = ar.getQueryString();
    assertEquals("param1=v13&param2=v21", queryString);
  }
}
