package org.opencommercesearch.api.client;

import org.apache.commons.lang.StringUtils;
import org.restlet.Request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a set of parameters.
 * <p/>
 * This class is not thread safe.
 *
 * @author jmendez
 */
public abstract class ApiRequest {

  /**
   * The URI of the current request. For example: /api-docs
   */
  private Map<String,String> params = new LinkedHashMap<String, String>();

  /**
   * Sets a param on the request. If a previous value existed, is replaced with the new one.
   * @param name The param name.
   * @param value The value for the given param name.
   */
  protected void setParam(String name, String value) {
    params.put(name, value);
  }

  /**
   * Gets the value for the given parameter name.
   * @param name The parameter name.
   * @return The value associated to the given parameter name.
   */
  protected String getParam(String name) {
    return params.get(name);
  }

  /**
   * Adds a param value to an existing value. By default, multivalued parameters are comma separated.
   * @param name The name of the param
   * @param value The new value to add
   */
  protected void addParam(String name, String value) {
    String currentValue = getParam(name);
    if(currentValue == null) {
      setParam(name, value);
    }
    else if(value != null) {
      setParam(name, currentValue + "," + value);
    }
  }

  /**
   * Converts this request to a valid query string.
   * @return A query string conformed of all parameters stored in this request.
   */
  protected String getQueryString() {
    if(params.isEmpty()) {
      return StringUtils.EMPTY;
    }

    StringBuilder queryString = new StringBuilder();

    for(String paramName: params.keySet()) {
      String paramValue = params.get(paramName);

      if(paramValue != null) {
        queryString.append(paramName);
        queryString.append("=");
        queryString.append(params.get(paramName));
        queryString.append("&");
      }
    }

    queryString.setLength(queryString.length() - 1);
    return queryString.toString();
  }

  /**
   * Get a Restlet request from this Api request.
   * @param url The API URL to be used.
   * @return A Restlet request initialized and
   */
  protected abstract Request asRestletRequest(String url);

  protected abstract String getEndpoint();
}
