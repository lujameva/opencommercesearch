package org.opencommercesearch.api.client.model;

import java.util.Map;

/**
 * Represents product summary entry data from an API search/browse response.
 * <p/>
 * Product summary could contain information such as lowest sku price, lowest sku sale price, among others.
 * @author jmendez
 */
public class ProductSummaryEntry {
  private String productId;
  private Map<String, Map<String, String>> data;

  public String getProductId() {
    return productId;
  }

  protected void setProductId(String productId) {
    this.productId = productId;
  }

  public Map<String, Map<String, String>> getData() {
    return data;
  }

  protected void setData(Map<String, Map<String, String>> data) {
    this.data = data;
  }
}
