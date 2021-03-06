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
package org.orcid.activitiesindexer.s3;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Resource;

import org.orcid.activitiesindexer.exception.DeprecatedRecordException;
import org.orcid.activitiesindexer.exception.LockedRecordException;
import org.orcid.activitiesindexer.orcid.Orcid20APIClient;
import org.orcid.activitiesindexer.persistence.managers.ActivitiesStatusManager;
import org.orcid.activitiesindexer.persistence.util.ActivityType;
import org.orcid.jaxb.model.record.summary_v2.ActivitiesSummary;
import org.orcid.jaxb.model.record.summary_v2.Educations;
import org.orcid.jaxb.model.record.summary_v2.Employments;
import org.orcid.jaxb.model.record.summary_v2.FundingGroup;
import org.orcid.jaxb.model.record.summary_v2.FundingSummary;
import org.orcid.jaxb.model.record.summary_v2.Fundings;
import org.orcid.jaxb.model.record.summary_v2.PeerReviewGroup;
import org.orcid.jaxb.model.record.summary_v2.PeerReviewSummary;
import org.orcid.jaxb.model.record.summary_v2.PeerReviews;
import org.orcid.jaxb.model.record.summary_v2.WorkGroup;
import org.orcid.jaxb.model.record.summary_v2.WorkSummary;
import org.orcid.jaxb.model.record.summary_v2.Works;
import org.orcid.jaxb.model.record_v2.Activity;
import org.orcid.jaxb.model.record_v2.AffiliationType;
import org.orcid.jaxb.model.record_v2.Record;
import org.orcid.utils.DateUtils;
import org.orcid.utils.listener.BaseMessage;
import org.orcid.utils.listener.LastModifiedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * Core logic for listeners
 * 
 * @author tom
 *
 */
@Component
public class S3MessageProcessor implements Consumer<LastModifiedMessage> {

    public static final String VND_ORCID_XML = "application/vnd.orcid+xml";
    public static final String VND_ORCID_JSON = "application/vnd.orcid+json";

    Logger LOG = LoggerFactory.getLogger(S3MessageProcessor.class);

    @Value("${org.orcid.message-listener.index.activities:true}")
    private boolean isActivitiesIndexerEnabled;
    
    @Resource
    private Orcid20APIClient orcid20ApiClient;
    @Resource
    private S3Manager s3Manager;
    @Resource
    private ExceptionHandler exceptionHandler;
    @Resource
    private ActivitiesStatusManager activitiesStatusManager;

    /**
     * Populates the Amazon S3 buckets
     */
    public void accept(LastModifiedMessage m) {
        update20Activities(m);        
    }
    
    public void retry(String orcid, List<ActivityType> types) {
        if(!isActivitiesIndexerEnabled) {
            return;
        }
        
        LOG.info("Retrying activities for record " + orcid);
        Record record = fetchPublicRecord(orcid);        
        ActivitiesSummary as = getActivitiesSummaryFromRecord(record);
        if(as != null) {
            Map<ActivityType, Map<String, S3ObjectSummary>> existingActivities = s3Manager.searchActivities(orcid);
            for(ActivityType type : types) {
                switch(type) {
                case EDUCATIONS:
                    processEducations(orcid, as.getEducations(), existingActivities.get(ActivityType.EDUCATIONS));
                    break;
                case EMPLOYMENTS:
                    processEmployments(orcid, as.getEmployments(), existingActivities.get(ActivityType.EMPLOYMENTS));
                    break;
                case FUNDINGS:
                    processFundings(orcid, as.getFundings(), existingActivities.get(ActivityType.FUNDINGS));
                    break;
                case PEER_REVIEWS:
                    processPeerReviews(orcid, as.getPeerReviews(), existingActivities.get(ActivityType.PEER_REVIEWS));
                    break;
                case WORKS:
                    processWorks(orcid, as.getWorks(), existingActivities.get(ActivityType.WORKS));
                    break;
                }
            }
        }       
    }

    private void update20Activities(BaseMessage message) {
        if(!isActivitiesIndexerEnabled) {
            return;
        }
        
        String orcid = message.getOrcid();
        LOG.info("Processing activities for record " + orcid);
        Record record = fetchPublicRecord(orcid);        
        ActivitiesSummary as = getActivitiesSummaryFromRecord(record);
        if (as != null) {
            Map<ActivityType, Map<String, S3ObjectSummary>> existingActivities = s3Manager.searchActivities(orcid);
            processEducations(orcid, as.getEducations(), existingActivities.get(ActivityType.EDUCATIONS));
            processEmployments(orcid, as.getEmployments(), existingActivities.get(ActivityType.EMPLOYMENTS));
            processFundings(orcid, as.getFundings(), existingActivities.get(ActivityType.FUNDINGS));
            processPeerReviews(orcid, as.getPeerReviews(), existingActivities.get(ActivityType.PEER_REVIEWS));
            processWorks(orcid, as.getWorks(), existingActivities.get(ActivityType.WORKS));                        
        } 
    }

    private void processEducations(String orcid, Educations educations, Map<String, S3ObjectSummary> existingElements) {
        LOG.info("Processing Educations for record " + orcid);
        if (educations != null && !educations.getSummaries().isEmpty()) {
            processActivities(orcid, educations.getSummaries(), existingElements, ActivityType.EDUCATIONS);
        } else {
            s3Manager.clearActivitiesByType(orcid, ActivityType.EDUCATIONS);
            activitiesStatusManager.markAsSent(orcid, ActivityType.EDUCATIONS);
        }                
    }
    
    private void processEmployments(String orcid, Employments employments, Map<String, S3ObjectSummary> existingElements) {
        LOG.info("Processing Employments for record " + orcid);
        if (employments != null && !employments.getSummaries().isEmpty()) {
            processActivities(orcid, employments.getSummaries(), existingElements, ActivityType.EMPLOYMENTS);
        } else {
            s3Manager.clearActivitiesByType(orcid, ActivityType.EMPLOYMENTS);
            activitiesStatusManager.markAsSent(orcid, ActivityType.EMPLOYMENTS);
        }
    }
    
    private void processFundings(String orcid, Fundings fundingsElement, Map<String, S3ObjectSummary> existingElements) {
        LOG.info("Processing Fundings for record " + orcid);
        if (fundingsElement != null && !fundingsElement.getFundingGroup().isEmpty()) {
            List<FundingSummary> fundings = new ArrayList<FundingSummary>();
            for (FundingGroup g : fundingsElement.getFundingGroup()) {
                fundings.addAll(g.getFundingSummary());
            }
            processActivities(orcid, fundings, existingElements, ActivityType.FUNDINGS);
        } else {
            s3Manager.clearActivitiesByType(orcid, ActivityType.FUNDINGS);
            activitiesStatusManager.markAsSent(orcid, ActivityType.FUNDINGS);
        }
    }
    
    private void processPeerReviews(String orcid, PeerReviews peerReviewsElement, Map<String, S3ObjectSummary> existingElements) {
        LOG.info("Processing PeerReviews for record " + orcid);
        if (peerReviewsElement != null && !peerReviewsElement.getPeerReviewGroup().isEmpty()) {
            List<PeerReviewSummary> peerReviews = new ArrayList<PeerReviewSummary>();
            for (PeerReviewGroup g : peerReviewsElement.getPeerReviewGroup()) {
                peerReviews.addAll(g.getPeerReviewSummary());
            }
            processActivities(orcid, peerReviews, existingElements, ActivityType.PEER_REVIEWS);
        } else {
            s3Manager.clearActivitiesByType(orcid, ActivityType.PEER_REVIEWS);
            activitiesStatusManager.markAsSent(orcid, ActivityType.PEER_REVIEWS);
        }
    }
    
    private void processWorks(String orcid, Works worksElement, Map<String, S3ObjectSummary> existingElements) {
        LOG.info("Processing Works for record " + orcid);
        if (worksElement != null && !worksElement.getWorkGroup().isEmpty()) {
            List<WorkSummary> works = new ArrayList<WorkSummary>();
            for (WorkGroup g : worksElement.getWorkGroup()) {
                works.addAll(g.getWorkSummary());
            }
            processActivities(orcid, works, existingElements, ActivityType.WORKS);
        } else {
            s3Manager.clearActivitiesByType(orcid, ActivityType.WORKS);
            activitiesStatusManager.markAsSent(orcid, ActivityType.WORKS);
        }
    }  
    
    private void processActivities(String orcid, List<? extends Activity> activities, Map<String, S3ObjectSummary> existingElements, ActivityType type) {
        try {
            for (Activity x : activities) {
                String putCodeString = String.valueOf(x.getPutCode());
                Activity activity = null;
                if (existingElements.containsKey(putCodeString)) {
                    S3ObjectSummary existingObject = existingElements.get(putCodeString);
                    Date elementLastModified = DateUtils.convertToDate(x.getLastModifiedDate().getValue());
                    Date s3LastModified = existingObject.getLastModified();
                    if (elementLastModified.after(s3LastModified)) {
                        activity = fetchActivity(orcid, x.getPutCode(), type);
                    }
                    // Remove it from the existingElements list since it was
                    // already processed
                    existingElements.remove(putCodeString);
                } else {
                    activity = fetchActivity(orcid, x.getPutCode(), type);
                }

                if (activity != null) {
                    // Upload it to S3
                    s3Manager.uploadActivity(orcid, putCodeString, activity);
                    // Remove it from the existingElements list means that the
                    // elements was already processed
                    existingElements.remove(putCodeString);
                }
            }
            // Remove from S3 all element that still exists on the
            // existingEducations map
            for (String putCode : existingElements.keySet()) {
                s3Manager.removeActivity(orcid, putCode, type);
            }

            // Mark them as sent
            activitiesStatusManager.markAsSent(orcid, type);
        } catch (Exception e) {
            LOG.error("Unable to fetch activities " + type.getValue() + " for orcid " + orcid);
            // Mark collection as failed
            activitiesStatusManager.markAsFailed(orcid, type);
            throw new RuntimeException(e);
        }

    }

    private Activity fetchActivity(String orcid, Long putCode, ActivityType type) {
        switch (type) {
        case EDUCATIONS:
            return orcid20ApiClient.fetchAffiliation(orcid, putCode, AffiliationType.EDUCATION);
        case EMPLOYMENTS:
            return orcid20ApiClient.fetchAffiliation(orcid, putCode, AffiliationType.EMPLOYMENT);
        case FUNDINGS:
            return orcid20ApiClient.fetchFunding(orcid, putCode);
        case PEER_REVIEWS:
            return orcid20ApiClient.fetchPeerReview(orcid, putCode);
        case WORKS:
            return orcid20ApiClient.fetchWork(orcid, putCode);
        default:
            throw new IllegalArgumentException("Invalid type! Imposible: " + type);
        }
    }
    
    private Record fetchPublicRecord(String orcid) {
        try {            
            return orcid20ApiClient.fetchPublicRecord(orcid);
        } catch (LockedRecordException | DeprecatedRecordException e) {
            // Remove all activities from this record
            s3Manager.clearActivities(orcid);
            // Mark all activities as ok
            activitiesStatusManager.markAllAsSent(orcid);
        } catch (Exception e) {
            // Mark all activities as failed
            activitiesStatusManager.markAllAsFailed(orcid);
            throw new RuntimeException(e);
        }
        
        return null;
    }   
    
    private ActivitiesSummary getActivitiesSummaryFromRecord(Record record) {
        if(record != null && record.getHistory() != null && record.getHistory().getClaimed() != null && record.getHistory().getClaimed() == true) {
            return record.getActivitiesSummary();
        } else if(record != null && record.getHistory() != null && record.getHistory().getClaimed() != null && record.getHistory().getClaimed() == false) {
            LOG.warn(record.getOrcidIdentifier().getPath() + " is unclaimed, so, his activities would not be indexed");
        }
        
        return null;
    }
}
