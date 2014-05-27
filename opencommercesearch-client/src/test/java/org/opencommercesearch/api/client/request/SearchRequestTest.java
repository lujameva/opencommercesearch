package org.opencommercesearch.api.client.request;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restlet.Request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author jmendez
 */
public class SearchRequestTest {
  @Test
  public void testGetHttpRequest() {
    SearchRequest sr = new SearchRequest();
    sr.setQuery("some query");
    sr.addField("field1");
    sr.addField("field2");
    sr.addFilterQuery("country:us");
    sr.addFilterQuery("price:[300 TO 400]");
    sr.setOffset(0);
    sr.setLimit(10);
    sr.setOutlet(false);
    sr.setSort("price asc, discount desc");
    sr.setSite("testSite");

    Request request = sr.asRestletRequest("http://localhost:9000/v1" + sr.getEndpoint() + "?preview=false");

    assertNotNull(request);
    assertEquals("GET", request.getMethod().getName());
    assertEquals("http://localhost:9000/v1/products?preview=false&q=some%20query&fields=field1,field2&filterQueries=country:us,price:[300%20TO%20400]&offset=0&limit=10&outlet=false&sort=price%20asc,%20discount%20desc&site=testSite",
        request.getResourceRef().toString());
  }
}
