package org.opencommercesearch.feed;

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

import atg.beans.DynamicPropertyDescriptor;
import atg.json.JSONArray;
import atg.json.JSONException;
import atg.json.JSONObject;
import atg.nucleus.GenericService;
import atg.nucleus.ServiceException;
import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;
import atg.repository.RepositoryView;
import atg.repository.rql.RqlStatement;
import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.api.ProductService;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StreamRepresentation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.opencommercesearch.api.ProductService.Endpoint;

/**
 * Base feed class that sends repository items to the OCS REST API.
 * <p/>
 * Data is transferred in JSON format and through regular HTTP calls. Child classes should handle
 * repository item to JSON transforms.
 * <p/>
 * Notice that the API must be aware of the repository item being fed (checkout opencommercesearch-api project).
 * <p/>
 * A feed can be transactional or not. When using transactions, all items will be cleared first. If an exception happens
 * all changes will be rolled back. If not, changes are committed. Be aware when using Solr. Solr transactions are not isolated.
 * Make sure the SolrCore doesn't have auto commits enabled.
 *
 * Non transactional feeds will delete all items that were not updated during the feed.
 *
 * @author jmendez
 */
public abstract class BaseRestFeed extends GenericService {

    /**
     * Repository to query for items
     */
    private Repository repository;

    /**
     * The actual item descriptor name being fed.
     */
    private String itemDescriptorName;

    /**
     * RQL query to get total items to be fed
     */
    private RqlStatement countRql;

    /**
     * Actual RQL that will get all the items to be fed (and fields for each item)
     */
    private RqlStatement rql;

    /**
     * Number of items that will be sent on each request
     */
    private int batchSize;

    /**
     * Whether or not this feed is enabled.
     */
    private boolean enabled;

    /**
     * Max error percentage tolerated by this feed. If this threshold is reached, then the feed will be discarded
     * since it will be considered risky. I.e. set it to 0.1 if you want a maximum of 10% errors of the total items
     * cause the feed to stop.
     */
    private double errorThreshold;

    /**
     * Whether or not this feeds is transactional.
     */
    private boolean transactional = true;

    protected ProductService productService;

    protected String endpointUrl;

    private Map<String, String> customPropertyMappings;

    /**
     * The custom property mappings loaded based on customPropertyMappings. For example
     * category.shortDisplayName -> alias becomes category -> (shortDisplayName -> alias)
     */
    private Map<String, Map<String,String>> itemDescriptorCustomPropertyMappings;

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public String getItemDescriptorName() {
        return itemDescriptorName;
    }

    public void setItemDescriptorName(String itemDescriptorName) {
        this.itemDescriptorName = itemDescriptorName;
    }

    public RqlStatement getCountRql() {
        return countRql;
    }

    public void setCountRql(RqlStatement countRql) {
        this.countRql = countRql;
    }

    public RqlStatement getRql() {
        return rql;
    }

    public void setRql(RqlStatement rql) {
        this.rql = rql;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    public boolean isTransactional() {
        return transactional;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ProductService getProductService() {
        return productService;
    }

    public void setProductService(ProductService productService) {
        this.productService = productService;
    }

    public double getErrorThreshold() {
        return errorThreshold;
    }

    public void setErrorThreshold(double errorThreshold) {
        this.errorThreshold = errorThreshold;
    }


    @Override
    public void doStartService() throws ServiceException {
        if (getProductService() == null) {
            throw new ServiceException("No productService found");
        }
        endpointUrl = getProductService().getUrl4Endpoint(getEndpoint());

        if (getCustomPropertyMappings() != null && getCustomPropertyMappings().size() > 0) {
            initializeCustomPropertyMappings();
        }
    }

    /**
     * Helper method to initialize the custom properties for each item descriptor
     */
    private void initializeCustomPropertyMappings() {
        try {
            itemDescriptorCustomPropertyMappings = new HashMap<String, Map<String, String>>();
            for (Map.Entry<String, String> entry : getCustomPropertyMappings().entrySet()) {
                String[] parts = StringUtils.split(entry.getKey(), '.');

                if (parts.length == 2) {
                    String itemDescriptorName = parts[0];
                    String propertyName = parts[1];
                    String propertyAlias = entry.getValue();

                    processMapping(itemDescriptorName, propertyName, propertyAlias);
                } else {
                    throw new IllegalArgumentException("Invalid nested property '" + entry.getKey() + "'");
                }
            }
        } catch (RepositoryException ex) {
            if (isLoggingError()) {
                logDebug("Cannot load category descriptor", ex);
            }
        }
    }

    /**
     * Helper method to process a mapping for an item descriptor
     *
     * @param itemDescriptorName is the item descriptor name
     * @param propertyName is the property name in the item descriptor
     * @param propertyAlias is the property alias to be used in the JSON objects
     */
    private void processMapping(String itemDescriptorName, String propertyName, String propertyAlias) throws RepositoryException {
        RepositoryItemDescriptor itemDescriptor = getRepository().getItemDescriptor(itemDescriptorName);

        if (itemDescriptor != null) {
            DynamicPropertyDescriptor propertyDescriptor = itemDescriptor.getPropertyDescriptor(propertyName);

            if (propertyDescriptor != null) {
                Map<String, String> mappings = itemDescriptorCustomPropertyMappings.get(itemDescriptorName);

                if (mappings == null) {
                    mappings = new LinkedHashMap<String, String>();
                    itemDescriptorCustomPropertyMappings.put(itemDescriptorName, mappings);
                }
                mappings.put(propertyName, propertyAlias);
            } else {
                if (isLoggingError()) {
                    logError("Property descriptor not found for '" + propertyName + "' in item descriptor '" + itemDescriptor.getItemDescriptorName()  + "'");
                }
            }
        } else {
            if (isLoggingError()) {
                logError("Item descriptor not found '" + itemDescriptorName + "'");
            }
        }
    }

    /**
     * Helper method to populate custom properties from a repository item into a jsonObject
     */
    protected void setCustomProperties(RepositoryItem item, JSONObject json) throws RepositoryException, JSONException {
        if (itemDescriptorCustomPropertyMappings == null || itemDescriptorCustomPropertyMappings.size() == 0) {
            return;
        }

        String itemDescriptorName = item.getItemDescriptor().getItemDescriptorName();
        Map<String, String> mappings = itemDescriptorCustomPropertyMappings.get(itemDescriptorName);
        if (mappings != null) {
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String jsonPropertyName = entry.getValue();
                Object propertyValue = item.getPropertyValue(entry.getKey());
                json.put(jsonPropertyName, propertyValue);
            }
        }
    }

    /**
     * Start running this feed.
     * @throws RepositoryException If there are problems reading the repository items from the database.
     */
    public void startFeed() throws RepositoryException, IOException {
        if(!isEnabled()) {
            if (isLoggingInfo()) {
                logInfo("Did not start feed for " + itemDescriptorName + " since is disabled. Verify your configuration is correct." );
            }

            return;
        }

        long startTime = System.currentTimeMillis();
        int processed = 0;
        int failed = 0;

        RepositoryView itemView = getRepository().getView(itemDescriptorName);
        int count = countRql.executeCountQuery(itemView, null);
        int errorThreshold = (int) (count * getErrorThreshold());

        if (isLoggingInfo()) {
            logInfo("Started " + itemDescriptorName + " feed for " + count + " items." );
        }

        try {
            long feedTimestamp = System.currentTimeMillis();

            if(count > 0) {
                if (isTransactional()) {
                    sendDeleteByQuery();
                }

                Integer[] rqlArgs = new Integer[] { 0, getBatchSize() };
                RepositoryItem[] items = rql.executeQueryUncached(itemView, rqlArgs);

                while (items != null) {
                    try {
                        int sent = sendItems(items, feedTimestamp);
                        processed += sent;
                        failed += items.length - sent;
                    }
                    catch (Exception ex) {
                        failed++;
                        if (isLoggingError()) {
                            logError("Cannot send " + itemDescriptorName + "[" + getIdsFromItemsArray(items) + "]", ex);
                        }
                    }

                    if (isLoggingInfo()) {
                        logInfo("Processed " + processed + " " + itemDescriptorName + " items out of " + count + " with " + failed + " failures");
                    }

                    if(failed < errorThreshold) {
                        //Get the next batch only if the feed is performing well.
                        rqlArgs[0] += getBatchSize();
                        items = rql.executeQueryUncached(itemView, rqlArgs);
                    }
                    else {
                        //Error threshold reached. Stop.
                        break;
                    }
                }

                if(failed < errorThreshold) {
                    //Send commit or deletes if the feeds looks healthy.
                    if (isTransactional()) {
                        sendCommit();
                    } else {
                        sendDelete(feedTimestamp);
                    }
                }
                else {
                    if(isLoggingError()) {
                        logError(itemDescriptorName + " feed interrupted since it seems to be failing too often. At least " + (getErrorThreshold() * 100) + "% out of " + count + " items had errors");
                    }

                    if (isTransactional()) {
                        //Roll back as much as we can from the changes done before the threshold was reached (specially initial delete)
                        sendRollback();
                    }
                }
            }
            else {
                if(isLoggingInfo()) {
                    logInfo("No " + itemDescriptorName + " items found. Nothing to do here.");
                }
            }
        }
        catch(Exception e) {
            if(isLoggingError()) {
                logError("Error while processing feed.", e);
            }

            if (isTransactional()) {
                sendRollback();
            }
        }

        if (isLoggingInfo()) {
            logInfo(itemDescriptorName + " feed finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds, " +
                    processed + " processed items and " + failed + " failures.");
        }
    }

    /**
     * Convert the given items to a JSON list and post them to the given API endpoint.
     * @param itemList The list of repository items to be sent.
     * @param feedTimestamp is the feed timestamp
     * @return The total count of items sent.
     * @throws RepositoryException if item data from the list can't be read.
     */
    private int sendItems(RepositoryItem[] itemList, long feedTimestamp) throws RepositoryException{
        int sent = 0;

        try {
            final JSONArray jsonObjects = new JSONArray();

            for (RepositoryItem item : itemList) {
                JSONObject json = repositoryItemToJson(item);
                setCustomProperties(item, json);

                if(json == null) {
                    if (isLoggingDebug()) {
                        logDebug("Sending " + itemDescriptorName + "[" + item.getRepositoryId()
                                + "] failed because it is missing required information. Expected: " + Arrays.toString(getRequiredItemFields()));
                    }
                }
                else {
                    jsonObjects.add(json);
                    sent++;
                }
            }

            //If all items were ignored due data errors, simply ignore this batch.
            if(jsonObjects.isEmpty()) {
                if(isLoggingDebug()) {
                    logDebug("Nothing to do here, all items in the current batch seem to have failed.");
                }

                return 0;
            }

            final JSONObject obj = new JSONObject();
            obj.put(getEndpoint().getLowerCaseName(), jsonObjects);
            obj.put("feedTimestamp", feedTimestamp);
            final StreamRepresentation representation = new StreamRepresentation(MediaType.APPLICATION_JSON) {
                @Override
                public InputStream getStream() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void write(OutputStream outputStream) throws IOException {
                    try {
                        OutputStreamWriter writer = new OutputStreamWriter(outputStream);
                        obj.write(writer);
                        writer.flush();
                    } catch (JSONException ex) {
                        throw new IOException("Cannot write JSON", ex);
                    }
                }
            };
            final Request request = new Request(Method.PUT, endpointUrl, new EncodeRepresentation(Encoding.GZIP, representation));
            Response response = null;

            try {
                response = getProductService().handle(request);
                if (!response.getStatus().equals(Status.SUCCESS_CREATED)) {
                    if (isLoggingInfo()) {
                        logInfo("Sending " + itemDescriptorName + "[" + getIdsFromItemsArray(itemList) + "] failed with status: " + response.getStatus() + " ["
                                + errorResponseToString(response.getEntity()) + "]");
                    }

                    return 0;
                }
            } finally {
                if (response != null) {
                    response.release();
                }
                if (request != null) {
                    request.release();
                }
            }

            return sent;
        } catch (JSONException ex) {
            if (isLoggingInfo()) {
                logInfo("Cannot create JSON representation for " + itemDescriptorName + " info [" + getIdsFromItemsArray(itemList) + "]");
            }

            return 0;
        }
    }

    /**
     * Sends a commit request to the API.
     * @throws IOException if the commit fails.
     */
    protected void sendCommit() throws IOException {
        String commitEndpointUrl = productService.getUrl4Endpoint(getEndpoint(), "commit");

        final Request request = new Request(Method.POST, commitEndpointUrl);
        Response response = null;

        try {
            response = getProductService().handle(request);
            if (!response.getStatus().equals(Status.SUCCESS_OK)) {
                throw new IOException("Failed to send commit with status " + response.getStatus() + " " + errorResponseToString(response.getEntity()));
            }
        } finally {
            if (response != null) {
                response.release();
            }
            if (request != null) {
                request.release();
            }
        }
    }

    /**
     * Sends a rollback request to the API.
     * @throws IOException if the rollback fails.
     */
    protected void sendRollback() throws IOException {
        String rollbackEndpointUrl = productService.getUrl4Endpoint(getEndpoint(), "rollback");

        final Request request = new Request(Method.POST, rollbackEndpointUrl);
        Response response = null;

        try {
            response = getProductService().handle(request);
            if (!response.getStatus().equals(Status.SUCCESS_OK)) {
                throw new IOException("Failed to send rollback with status " + response.getStatus() + " " + errorResponseToString(response.getEntity()));
            }
        } finally {
            if (response != null) {
                response.release();
            }
            if (request != null) {
                request.release();
            }
        }
    }

    /**
     * Sends a delete by query request to the API.
     * @throws IOException if the delete fails.
     */
    protected void sendDeleteByQuery() throws IOException {
        String deleteEndpointUrl = endpointUrl;
        deleteEndpointUrl += (getProductService().getPreview())? "&" : "?";
        deleteEndpointUrl += "query=*:*";

        final Request request = new Request(Method.DELETE, deleteEndpointUrl);
        Response response = null;

        try {
            response = getProductService().handle(request);
            if (!response.getStatus().equals(Status.SUCCESS_OK)) {
                throw new IOException("Failed to send delete by query with status " + response.getStatus() + " " + errorResponseToString(response.getEntity()));
            }
        } finally {
            if (response != null) {
                response.release();
            }
            if (request != null) {
                request.release();
            }
        }
    }

    protected void sendDelete(long feedTimestamp) {
        String deleteEndpointUrl = endpointUrl;
        deleteEndpointUrl += (getProductService().getPreview())? "&" : "?";
        deleteEndpointUrl += "feedTimestamp=" + feedTimestamp;

        final Request request = new Request(Method.DELETE, deleteEndpointUrl);
        Response response = null;

        try {
            response = getProductService().handle(request);
            if (isLoggingInfo()) {
                if (response.getStatus().equals(Status.SUCCESS_NO_CONTENT)) {
                    logInfo("Successfully deleted " + itemDescriptorName + " items with feed timestamp before to " + feedTimestamp);
                } else {
                    logInfo("Deleting " + itemDescriptorName + " items with feed timestamp before to " + feedTimestamp + " failed with status: " + response.getStatus());
                }
            }
        } finally {
            if (response != null) {
                response.release();
            }
            if (request != null) {
                request.release();
            }
        }
    }

    /**
     * Get the IDs of all given repository items.
     * @param items Items to get the IDs from.
     * @return List of all IDs concatenated and separated by comma.
     */
    private String getIdsFromItemsArray(RepositoryItem[] items) {
        if (items == null || items.length == 0) {
            return StringUtils.EMPTY;
        }

        StringBuilder buffer = new StringBuilder();

        for (RepositoryItem item : items) {
            buffer.append(item.getRepositoryId()).append(", ");
        }

        //Remove extra comma
        buffer.setLength(buffer.length() - 2);
        return buffer.toString();
    }

    /**
     * Create a printable string out of an HTTP (restlet) error response.
     * @param representation The restlet representation of the error response.
     * @return A printable string containing the HTTP (restlet) error response.
     */
    protected String errorResponseToString(Representation representation) {
        String message = "unknown exception";
        try {
            String text = null;
            if(representation != null && (text = representation.getText()) != null) {
                JSONObject obj = new JSONObject(text);
                message = obj.getString("message");

                if(isLoggingDebug() && obj.has("detail")) {
                    message += "\n\n" + obj.getString("detail");
                }
            }
        }
        catch (JSONException ex) {
            if(isLoggingError()) {
                logError("Can't parse error response.", ex);
            }
        }
        catch (IOException ex) {
            if(isLoggingError()) {
                logError("Can't get error response.", ex);
            }
        }

        return message;
    }

    /**
     * The mappings for custom properties. For example, category.name -> alias
     * @return custom property mappings
     */
    public Map<String, String> getCustomPropertyMappings() {
      return customPropertyMappings;
    }

    /**
     * Sets the list of custom property mappings
     * @param customPropertyMappings
     */
    public void setCustomPropertyMappings(Map<String, String> customPropertyMappings) {
      this.customPropertyMappings = customPropertyMappings;
    }

    /**
     * Return the Endpoint for this feed
     * @return an Endpoint enum representing the endpoint for this feed
     */
    public abstract Endpoint getEndpoint();

    /**
     * Convert the given repository item to its corresponding JSON API format.
     * @param item Repository item to convert.
     * @return The JSON representation of the given repository item, or null if there are missing fields.
     * @throws JSONException if there are format issues when creating the JSON object.
     * @throws RepositoryException if item data from the list can't be read.
     */
    protected abstract JSONObject repositoryItemToJson(RepositoryItem item) throws JSONException, RepositoryException;

    /**
     * Return a list of required fields when transforming a repository item to JSON.
     * <p/>
     * This list is used for logging purposes only.
     * @return List of required fields when transforming a repository item to JSON, required for logging purposes.
     */
    protected abstract String[] getRequiredItemFields();
}

