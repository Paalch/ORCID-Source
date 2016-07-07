/**
 * =============================================================================
 *
 * ORCID (R) Open Source
 * http://orcid.org
 *
 * Copyright (c) 2012-2014 ORCID, Inc.
 * Licensed under an MIT-Style License (MIT)
 * http://orcid.org/open-source-license
 *
 * This copyright and license information (including a link to the full license)
 * shall be included in its entirety in all copies or substantial portion of
 * the software.
 *
 * =============================================================================
 */
package org.orcid.core.manager.impl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.orcid.core.exception.SalesForceUnauthorizedException;
import org.orcid.core.manager.SalesForceManager;
import org.orcid.pojo.SalesForceDetails;
import org.orcid.pojo.SalesForceIntegration;
import org.orcid.pojo.SalesForceMember;
import org.orcid.utils.ReleaseNameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.github.slugify.Slugify;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.representation.Form;

import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

/**
 * 
 * @author Will Simpson
 *
 */
public class SalesForceManagerImpl implements SalesForceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalesForceManager.class);

    @Value("${org.orcid.core.salesForce.clientId}")
    private String clientId;

    @Value("${org.orcid.core.salesForce.clientSecret}")
    private String clientSecret;

    @Value("${org.orcid.core.salesForce.username}")
    private String username;

    @Value("${org.orcid.core.salesForce.password}")
    private String password;

    @Value("${org.orcid.core.salesForce.tokenEndPointUrl:https://test.salesforce.com/services/oauth2/token}")
    private String tokenEndPointUrl;

    @Value("${org.orcid.core.salesForce.apiBaseUrl:https://cs43.salesforce.com}")
    private String apiBaseUrl;

    @Resource(name = "salesForceMembersListCache")
    private SelfPopulatingCache salesForceMembersListCache;

    @Resource(name = "salesForceMemberDetailsCache")
    private SelfPopulatingCache salesForceMemberDetailsCache;

    private Client client = Client.create();

    private String accessToken;

    private String releaseName = ReleaseNameUtils.getReleaseName();

    private Slugify slugify;
    {
        try {
            slugify = new Slugify();
        } catch (IOException e) {
            throw new RuntimeException("Error initializing slugify", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<SalesForceMember> retrieveMembers() {
        return (List<SalesForceMember>) salesForceMembersListCache.get(releaseName).getObjectValue();
    }

    @Override
    public List<SalesForceMember> retrieveFreshMembers() {
        try {
            return retrieveMembersFromSalesForce(getAccessToken());
        } catch (SalesForceUnauthorizedException e) {
            LOGGER.debug("Unauthorized to retrieve members list, trying again.", e);
            return retrieveMembersFromSalesForce(getFreshAccessToken());
        }
    }

    @Override
    public List<SalesForceMember> retrieveConsortia() {
        // XXX Implement cache
        return retrieveFreshConsortia();
    }

    @Override
    public List<SalesForceMember> retrieveFreshConsortia() {
        try {
            return retrieveConsortiaFromSalesForce(getAccessToken());
        } catch (SalesForceUnauthorizedException e) {
            LOGGER.debug("Unauthorized to retrieve consortia list, trying again.", e);
            return retrieveConsortiaFromSalesForce(getFreshAccessToken());
        }
    }

    @Override
    public SalesForceDetails retrieveDetails(String memberId, String consortiumLeadId) {
        return (SalesForceDetails) salesForceMemberDetailsCache.get(new SalesForceMemberDetailsCacheKey(memberId, consortiumLeadId, releaseName)).getObjectValue();
    }

    @Override
    public SalesForceDetails retrieveFreshDetails(String memberId, String consortiumLeadId) {
        validateSalesForceId(memberId);
        if (consortiumLeadId != null) {
            validateSalesForceId(consortiumLeadId);
        }
        try {
            return retrieveDetailsFromSalesForce(getAccessToken(), memberId, consortiumLeadId);
        } catch (SalesForceUnauthorizedException e) {
            LOGGER.debug("Unauthorized to retrieve details, trying again.", e);
            return retrieveDetailsFromSalesForce(getFreshAccessToken(), memberId, consortiumLeadId);
        }
    }

    @Override
    public SalesForceDetails retrieveDetailsBySlug(String memberSlug) {
        List<SalesForceMember> members = retrieveMembers();
        Optional<SalesForceMember> match = members.stream().filter(e -> memberSlug.equals(e.getSlug())).findFirst();
        if (match.isPresent()) {
            SalesForceMember salesForceMember = match.get();
            return (SalesForceDetails) salesForceMemberDetailsCache
                    .get(new SalesForceMemberDetailsCacheKey(salesForceMember.getId(), salesForceMember.getConsortiumLeadId(), releaseName)).getObjectValue();
        }
        throw new IllegalArgumentException("No member details found for " + memberSlug);
    }

    @Override
    public String validateSalesForceId(String salesForceId) {
        if (!salesForceId.matches("[a-zA-Z0-9]+")) {
            // Could be malicious, so give no further info.
            throw new IllegalArgumentException();
        }
        return salesForceId;
    }

    /**
     * 
     * @throws SalesForceUnauthorizedException
     *             If the status code from SalesForce is 401, e.g. access token
     *             expired.
     * 
     */
    private List<SalesForceMember> retrieveMembersFromSalesForce(String accessToken) throws SalesForceUnauthorizedException {
        LOGGER.info("About get list of members from SalesForce");
        WebResource resource = createMemberListResource();
        ClientResponse response = resource.header("Authorization", "Bearer " + accessToken).accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);
        checkAuthorization(response);
        if (response.getStatus() != 200) {
            throw new RuntimeException("Error getting member list from SalesForce, status code =  " + response.getStatus() + ", reason = "
                    + response.getStatusInfo().getReasonPhrase() + ", body = " + response.getEntity(String.class));
        }
        return createMembersListFromResponse(response);
    }

    private WebResource createMemberListResource() {
        WebResource resource = client.resource(apiBaseUrl).path("services/data/v20.0/query").queryParam("q",
                "SELECT Account.Id, Account.Name, Account.Website, Account.BillingCountry, Account.Research_Community__c, (SELECT Consortia_Lead__c from Opportunities), Account.Public_Display_Description__c, Account.Logo_Description__c from Account WHERE Active_Member__c=TRUE");
        return resource;
    }

    /**
     * 
     * @throws SalesForceUnauthorizedException
     *             If the status code from SalesForce is 401, e.g. access token
     *             expired.
     * 
     */
    private List<SalesForceMember> retrieveConsortiaFromSalesForce(String accessToken) throws SalesForceUnauthorizedException {
        LOGGER.info("About get list of consortia from SalesForce");
        WebResource resource = createConsortiaListResource();
        ClientResponse response = resource.header("Authorization", "Bearer " + accessToken).accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);
        checkAuthorization(response);
        if (response.getStatus() != 200) {
            throw new RuntimeException("Error getting consortia list from SalesForce, status code =  " + response.getStatus() + ", reason = "
                    + response.getStatusInfo().getReasonPhrase() + ", body = " + response.getEntity(String.class));
        }
        return createMembersListFromResponse(response);
    }

    private WebResource createConsortiaListResource() {
        WebResource resource = client.resource(apiBaseUrl).path("services/data/v20.0/query").queryParam("q",
                "SELECT Id, Name, Website, Research_Community__c, BillingCountry, Public_Display_Description__c, Logo_Description__c, (SELECT Opportunity.Id FROM Opportunities WHERE IsClosed=TRUE AND IsWon=TRUE AND Membership_End_Date__c > TODAY) from Account WHERE Id IN (SELECT Consortia_Lead__c FROM Opportunity) AND Active_Member__c=TRUE");
        return resource;
    }

    private List<SalesForceMember> createMembersListFromResponse(ClientResponse response) {
        List<SalesForceMember> members = new ArrayList<>();
        JSONObject results = response.getEntity(JSONObject.class);
        try {
            JSONArray records = results.getJSONArray("records");
            for (int i = 0; i < records.length(); i++) {
                members.add(createMemberFromSalesForceRecord(records.getJSONObject(i)));
            }
        } catch (JSONException e) {
            throw new RuntimeException("Error getting member records from SalesForce JSON", e);
        }
        return members;
    }

    private SalesForceMember createMemberFromSalesForceRecord(JSONObject record) throws JSONException {
        String name = extractString(record, "Name");
        SalesForceMember member = new SalesForceMember();
        member.setName(name);
        member.setSlug(slugify.slugify(name));
        member.setId(extractString(record, "Id"));
        try {
            member.setWebsiteUrl(extractURL(record, "Website"));
        } catch (MalformedURLException e) {
            LOGGER.info("Malformed website URL for member: {}", name, e);
        }
        member.setResearchCommunity(extractString(record, "Research_Community__c"));
        member.setCountry(extractString(record, "BillingCountry"));
        JSONObject opportunitiesObject = extractObject(record, "Opportunities");
        if (opportunitiesObject != null) {
            JSONArray opportunitiesArray = opportunitiesObject.getJSONArray("records");
            for (int i = 0; i < opportunitiesArray.length(); i++) {
                String consortiumLeadId = extractString(opportunitiesArray.getJSONObject(i), "Consortia_Lead__c");
                if (consortiumLeadId != null) {
                    member.setConsortiumLeadId(consortiumLeadId);
                }
            }
        }
        member.setDescription(extractString(record, "Public_Display_Description__c"));
        try {
            member.setLogoUrl(extractURL(record, "Logo_Description__c"));
        } catch (MalformedURLException e) {
            LOGGER.info("Malformed logo URL for member: {}", name, e);
        }
        return member;
    }

    /**
     * 
     * @throws SalesForceUnauthorizedException
     *             If the status code from SalesForce is 401, e.g. access token
     *             expired.
     * 
     */
    private SalesForceDetails retrieveDetailsFromSalesForce(String accessToken, String memberId, String consortiumLeadId) throws SalesForceUnauthorizedException {
        SalesForceDetails details = new SalesForceDetails();
        details.setParentOrgName(retrieveParentOrgNameFromSalesForce(accessToken, consortiumLeadId));
        details.setIntegrations(retrieveIntegrationsFromSalesForce(accessToken, memberId));
        return details;
    }

    private String retrieveParentOrgNameFromSalesForce(String accessToken, String consortiumLeadId) {
        if (consortiumLeadId == null) {
            return null;
        }
        WebResource resource = createParentOrgResource(consortiumLeadId);
        ClientResponse response = resource.header("Authorization", "Bearer " + accessToken).accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);
        checkAuthorization(response);
        if (response.getStatus() != 200) {
            throw new RuntimeException("Error getting integrations list from SalesForce, status code =  " + response.getStatus() + ", reason = "
                    + response.getStatusInfo().getReasonPhrase() + ", body = " + response.getEntity(String.class));
        }
        return extractParentOrgNameFromResponse(response);
    }

    private WebResource createParentOrgResource(String consortiumLeadId) {
        WebResource resource = client.resource(apiBaseUrl).path("services/data/v20.0/query").queryParam("q",
                "SELECT Name from Account WHERE Id='" + validateSalesForceId(consortiumLeadId) + "'");
        return resource;
    }

    private String extractParentOrgNameFromResponse(ClientResponse response) {
        JSONObject results = response.getEntity(JSONObject.class);
        try {
            JSONArray records = results.getJSONArray("records");
            if (records.length() > 0) {
                return extractString(records.getJSONObject(0), "Name");
            }
        } catch (JSONException e) {
            throw new RuntimeException("Error getting parent org from SalesForce JSON", e);
        }
        return null;
    }

    /**
     * 
     * @throws SalesForceUnauthorizedException
     *             If the status code from SalesForce is 401, e.g. access token
     *             expired.
     * 
     */
    private List<SalesForceIntegration> retrieveIntegrationsFromSalesForce(String accessToken, String memberId) throws SalesForceUnauthorizedException {
        WebResource resource = createIntegrationListResource(memberId);
        ClientResponse response = resource.header("Authorization", "Bearer " + accessToken).accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);
        checkAuthorization(response);
        if (response.getStatus() != 200) {
            throw new RuntimeException("Error getting integrations list from SalesForce, status code =  " + response.getStatus() + ", reason = "
                    + response.getStatusInfo().getReasonPhrase() + ", body = " + response.getEntity(String.class));
        }
        return createIntegrationsListFromResponse(response);
    }

    private WebResource createIntegrationListResource(String memberId) {
        WebResource resource = client.resource(apiBaseUrl).path("services/data/v20.0/query").queryParam("q",
                "SELECT (SELECT Integration__c.Name, Integration__c.Description__c, Integration__c.Integration_Stage__c, Integration__c.Integration_URL__c from Account.Integrations__r) from Account WHERE Id='"
                        + validateSalesForceId(memberId) + "'");
        return resource;
    }

    private List<SalesForceIntegration> createIntegrationsListFromResponse(ClientResponse response) {
        List<SalesForceIntegration> integrations = new ArrayList<>();
        JSONObject results = response.getEntity(JSONObject.class);
        try {
            JSONArray records = results.getJSONArray("records");
            if (records.length() > 0) {
                JSONObject firstRecord = records.getJSONObject(0);
                JSONObject integrationsObject = extractObject(firstRecord, "Integrations__r");
                if (integrationsObject != null) {
                    JSONArray integrationRecords = integrationsObject.getJSONArray("records");
                    for (int i = 0; i < integrationRecords.length(); i++) {
                        integrations.add(createIntegrationFromSalesForceRecord(integrationRecords.getJSONObject(i)));
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException("Error getting integrations records from SalesForce JSON", e);
        }
        return integrations;
    }

    private SalesForceIntegration createIntegrationFromSalesForceRecord(JSONObject integrationRecord) throws JSONException {
        SalesForceIntegration integration = new SalesForceIntegration();
        String name = extractString(integrationRecord, "Name");
        integration.setName(name);
        integration.setDescription(extractString(integrationRecord, "Description__c"));
        integration.setStage(extractString(integrationRecord, "Integration_Stage__c"));
        try {
            integration.setResourceUrl(extractURL(integrationRecord, "Integration_URL__c"));
        } catch (MalformedURLException e) {
            LOGGER.info("Malformed resource URL for member: {}", name, e);
        }
        return integration;
    }

    private JSONObject extractObject(JSONObject parent, String key) throws JSONException {
        if (parent.isNull(key)) {
            return null;
        }
        return parent.getJSONObject(key);
    }

    private String extractString(JSONObject record, String key) throws JSONException {
        if (record.isNull(key)) {
            return null;
        }
        return record.getString(key);
    }

    private URL extractURL(JSONObject record, String key) throws JSONException, MalformedURLException {
        String urlString = tidyUrl(extractString(record, key));
        return urlString != null ? new URL(urlString) : null;
    }

    private String tidyUrl(String urlString) {
        if (StringUtils.isBlank(urlString)) {
            return null;
        }
        if (!urlString.matches("^.*?://.*")) {
            urlString = "http://" + urlString;
        }
        return urlString;
    }

    private String getAccessToken() {
        if (accessToken == null) {
            accessToken = getFreshAccessToken();
        }
        return accessToken;
    }

    private String getFreshAccessToken() {
        LOGGER.info("About get SalesForce access token");
        WebResource resource = client.resource(tokenEndPointUrl);
        Form form = new Form();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("username", username);
        form.add("password", password);
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON_TYPE).type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).post(ClientResponse.class, form);
        if (response.getStatus() == 200) {
            try {
                return response.getEntity(JSONObject.class).getString("access_token");
            } catch (ClientHandlerException | UniformInterfaceException | JSONException e) {
                throw new RuntimeException("Unable to extract access token from response", e);
            }
        } else {
            throw new RuntimeException("Error getting access token from SalesForce, status code =  " + response.getStatus() + ", reason = "
                    + response.getStatusInfo().getReasonPhrase() + ", body = " + response.getEntity(String.class));
        }
    }

    private void checkAuthorization(ClientResponse response) {
        if (response.getStatus() == 401) {
            throw new SalesForceUnauthorizedException("Unauthorized reponse from SalesForce, status code =  " + response.getStatus() + ", reason = "
                    + response.getStatusInfo().getReasonPhrase() + ", body= " + response.getEntity(String.class));
        }
    }

}
