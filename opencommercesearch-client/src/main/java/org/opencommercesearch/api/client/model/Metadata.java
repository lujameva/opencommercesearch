package org.opencommercesearch.api.client.model;

import org.opencommercesearch.BreadCrumb;
import org.opencommercesearch.Facet;

/**
 * Simple data holder for response metadata.
 * @author jmendez
 */
public class Metadata {
  private int found;
  private int time;
  private ProductSummaryEntry[] productSummary;
  private Facet[] facets;
  private BreadCrumb[] breadCrumbs;

  /**
   * Gets the number of items found.
   * @return The number of items found.
   */
  public int getFound() {
    return found;
  }

  protected void setFound(int found) {
    this.found = found;
  }

  /**
   * Gets the time in milliseconds that the API server took to respond.
   * @return The time in milliseconds that the API server took to respond.
   */
  public int getTime() {
    return time;
  }

  protected void setTime(int time) {
    this.time = time;
  }

  public ProductSummaryEntry[] getProductSummary() {
    return productSummary;
  }

  protected void setProductSummary(ProductSummaryEntry[] productSummary) {
    this.productSummary = productSummary;
  }

  public Facet[] getFacets() {
    return facets;
  }

  protected void setFacets(Facet[] facets) {
    this.facets = facets;
  }

  public BreadCrumb[] getBreadCrumbs() {
    return breadCrumbs;
  }

  protected void setBreadCrumbs(BreadCrumb[] breadCrumbs) {
    this.breadCrumbs = breadCrumbs;
  }
}
