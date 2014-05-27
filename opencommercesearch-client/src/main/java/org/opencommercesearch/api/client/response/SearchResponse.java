package org.opencommercesearch.api.client.response;

import org.opencommercesearch.api.client.model.Metadata;
import org.opencommercesearch.api.client.model.Product;
import org.opencommercesearch.api.client.request.ApiResponse;

/**
 * Simple data holder that represents a response from search endpoints.
 * @author jmendez
 */
public class SearchResponse extends ApiResponse {
  private Metadata metadata;
  private Product[] products;

  public Metadata getMetadata() {
    return metadata;
  }

  protected void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public Product[] getProducts() {
    return products;
  }

  protected void setProducts(Product[] products) {
    this.products = products;
  }
}
