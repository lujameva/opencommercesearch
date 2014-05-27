package org.opencommercesearch.api.client.request;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.api.client.ApiRequest;
import org.restlet.Request;
import org.restlet.data.Method;

/**
 * @author jmendez
 */
public class SearchRequest extends ApiRequest {

  public void setQuery(String query) {
    setParam("q", query);
  }

  public void setSite(String site) {
    setParam("site", site);
  }

  public void setPreview(boolean preview) {
    setParam("preview", String.valueOf(preview));
  }

  public void setOutlet(boolean outlet) {
    setParam("outlet", String.valueOf(outlet));
  }

  public void setOffset(int offset) {
    setParam("offset", String.valueOf(offset));
  }

  public void setLimit(int limit) {
    setParam("limit", String.valueOf(limit));
  }

  public void addField(String fieldName) {
    addParam("fields", fieldName);
  }

  public void setFields(String[] fields) {
    if(fields != null) {
      setParam("fields", StringUtils.join(fields, ","));
    }
  }

  public void addFilterQuery(String filterQuery) {
    addParam("filterQueries", filterQuery);
  }

  public void setFilterQueries(String[] filterQueries) {
    if(filterQueries != null) {
      setParam("filterQueries", StringUtils.join(filterQueries, ","));
    }
  }

  public void setSort(String sort) {
    setParam("sort", sort);
  }

  @Override
  protected Request asRestletRequest(String url) {
    return new Request(Method.GET, url + "&" + getQueryString());
  }

  @Override
  protected String getEndpoint() {
    return "/products";
  }
}
