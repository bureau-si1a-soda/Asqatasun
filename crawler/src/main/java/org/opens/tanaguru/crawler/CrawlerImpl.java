package org.opens.tanaguru.crawler;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import org.archive.modules.deciderules.DecideRuleSequence;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.persistence.PersistenceException;
import org.apache.commons.httpclient.URIException;
import org.apache.log4j.Logger;
import org.archive.io.GzippedInputStream;
import org.archive.io.RecordingInputStream;
import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.MatchesFilePatternDecideRule;
import org.archive.modules.extractor.Link;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.opens.tanaguru.crawler.extractor.listener.ExtractorCSSListener;
import org.opens.tanaguru.crawler.extractor.listener.ExtractorHTMLListener;
import org.opens.tanaguru.crawler.framework.TanaguruCrawlJob;
import org.opens.tanaguru.crawler.util.CrawlUtils;
import org.opens.tanaguru.entity.audit.Content;
import org.opens.tanaguru.entity.audit.ImageContent;
import org.opens.tanaguru.entity.audit.RelatedContent;
import org.opens.tanaguru.entity.audit.SSP;
import org.opens.tanaguru.entity.audit.StylesheetContent;
import org.opens.tanaguru.entity.factory.subject.WebResourceFactory;
import org.opens.tanaguru.entity.factory.audit.ContentFactory;
import org.opens.tanaguru.entity.service.audit.ContentDataService;
import org.opens.tanaguru.entity.service.subject.WebResourceDataService;
import org.opens.tanaguru.entity.subject.Page;
import org.opens.tanaguru.entity.subject.Site;
import org.opens.tanaguru.entity.subject.WebResource;
import org.opens.tanaguru.util.MD5Encoder;

/**
 * 
 * @author ADEX
 */
public class CrawlerImpl implements Crawler, ExtractorHTMLListener, ExtractorCSSListener, ContentWriter {

    private static final Logger LOGGER = Logger.getLogger(CrawlerImpl.class);
    private static final int RETRIEVE_WINDOW = 1000;
    private static final String UNREACHABLE_RESOURCE_STR =
            "Unreachable resource ";
    private boolean isPageAlreadyFetched = false;
    private WebResource mainWebResource;
    private String heritrixSiteFileName = "tanaguru-crawler-beans-site.xml";
    private String heritrixPageFileName = "tanaguru-crawler-beans-page.xml";
    private String crawlConfigFilePath;
    private String outputDir = System.getenv("PWD") + "/output";
    private Pattern cssFilePattern = null;
    private TanaguruCrawlJob crawlJob;
    private DecideRuleSequence decideRuleSequence;
    private Map<String, Long> persistedOutlinksMap = new HashMap<String, Long>();
    private Map<Long, List<Long>> relatedCssMap = new HashMap<Long, List<Long>>();
    private ContentDataService contentDataService;
    private WebResourceDataService webResourceDataService;
    private WebResourceFactory webResourceFactory;
    private ContentFactory contentFactory;

    public DecideRuleSequence getDecideRuleSequence() {
        if (decideRuleSequence == null && crawlJob != null) {
            decideRuleSequence = crawlJob.getDecideRuleSequence();
        }
        return decideRuleSequence;
    }

    public Pattern getCssFilePattern() {
        if (cssFilePattern == null && crawlJob != null) {
            cssFilePattern = crawlJob.getCssFilePattern();
        }
        return cssFilePattern;
    }
    private Pattern htmlFilePattern = null;

    public Pattern getHtmlFilePattern() {
        if (htmlFilePattern == null && crawlJob != null) {
            htmlFilePattern = crawlJob.getHtmlFilePattern();
        }
        return htmlFilePattern;
    }

    public ContentDataService getContentDataService() {
        return contentDataService;
    }

    public void setContentDataService(ContentDataService contentDataService) {
        this.contentDataService = contentDataService;
    }

    public WebResourceDataService getWebResourceDataService() {
        return webResourceDataService;
    }

    public void setWebResourceDataService(WebResourceDataService webResourceDataService) {
        this.webResourceDataService = webResourceDataService;
    }

    @Override
    public void setWebResourceFactory(WebResourceFactory webResourceFactory) {
        this.webResourceFactory = webResourceFactory;
    }

    public void setContentFactory(ContentFactory contentFactory) {
        this.contentFactory = contentFactory;
    }

    public CrawlerImpl() {
        super();
    }

    public String getCrawlConfigFilePath() {
        return this.crawlConfigFilePath;
    }

    public void setCrawlConfigFilePath(String crawlConfigFilePath) {
        this.crawlConfigFilePath = crawlConfigFilePath;
    }

    public String getOutputDir() {
        return this.outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getSiteURL() {
        return mainWebResource.getURL();
    }

    @Override
    public void setSiteURL(String siteURL) {
        mainWebResource = webResourceFactory.createSite(siteURL);
        mainWebResource = webResourceDataService.saveOrUpdate(mainWebResource);
        String[] siteUrl = {siteURL};
        this.crawlJob = new TanaguruCrawlJob(siteUrl, heritrixSiteFileName, getOutputDir(), getCrawlConfigFilePath());
        if (crawlJob.isLaunchable()) {
            crawlJob.checkXML();
        }
        persistedOutlinksMap.clear();
    }

    /**
     *
     * @param siteUrl
     */
    @Override
    public void setSiteURL(String siteName, String[] siteURL) {
        mainWebResource = webResourceFactory.createSite(siteName);
        mainWebResource = webResourceDataService.saveOrUpdate(mainWebResource);
        this.crawlJob = new TanaguruCrawlJob(siteURL, heritrixPageFileName, outputDir, crawlConfigFilePath);
        if (crawlJob.isLaunchable()) {
            crawlJob.checkXML();
        }
        persistedOutlinksMap.clear();
    }

    /**
     *
     * @param siteUrl
     */
    @Override
    public void setPageURL(String pageURL) {
        mainWebResource = webResourceFactory.createPage(pageURL);
        mainWebResource = webResourceDataService.saveOrUpdate(mainWebResource);
        String[] pageUrl = {pageURL};
        this.crawlJob = new TanaguruCrawlJob(pageUrl, heritrixPageFileName, outputDir, crawlConfigFilePath);
        if (crawlJob.isLaunchable()) {
            crawlJob.checkXML();
        }
        isPageAlreadyFetched = false;
        persistedOutlinksMap.clear();
    }

    @Override
    public WebResource getResult() {
        crawlJob = null;
        decideRuleSequence = null;
        cssFilePattern = null;
        htmlFilePattern = null;
        isPageAlreadyFetched = false;
        return mainWebResource;
    }

    @Override
    public void run() {
        this.crawlJob.setExtractorCSSListener(this);
        this.crawlJob.setExtractorHTMLListener(this);
        this.crawlJob.setContentWriter(this);
        this.crawlJob.launchCrawlJob();
        removeOrphanContent();
    }

    @Override
    public void computeAndPersistSuccessfullFetchedResource(
            CrawlURI curi,
            RecordingInputStream recis) throws IOException {
        LOGGER.debug("Writing " + curi.getURI() + " : "
                + curi.getFetchStatus());
        if (curi.getContentType().contains(ContentType.html.getType())
                && !curi.getURI().contains("robots.txt")) {
            WebResource wr =
                    webResourceDataService.getByUrlAndParentWebResource(
                    curi.getURI(),
                    mainWebResource);
            if (wr == null && mainWebResource instanceof Page) {
                if (isPageAlreadyFetched) {
                    return;
                }
                wr = mainWebResource;
                // in case of redirection, we modify the URI of the webresource
                // to ensure the webresource and its SSP have the same URI.
                wr.setURL(curi.getURI());
                isPageAlreadyFetched = true;
            }
            // extract data from fetched content and record it to SSP object
            String charset = CrawlUtils.extractCharset(recis.getContentReplayInputStream());
            String sourceCode = CrawlUtils.convertSourceCodeIntoUtf8(recis, charset);
            Content htmlContent = contentDataService.findSSP(wr, curi.getURI());
            if (htmlContent != null) {
                ((SSP) htmlContent).setCharset(charset);
                ((SSP) htmlContent).setSource(sourceCode);
                saveAndPersistFetchDataToContent(htmlContent, curi);
                webResourceDataService.saveOrUpdate(wr);
            }
        } else if (curi.getContentType().contains(ContentType.css.getType())) {
            boolean compressed = GzippedInputStream.isCompressedStream(recis.getContentReplayInputStream());
            String cssCode = null;
            if (compressed) {
                cssCode = "";
            } else {
                String charset = CrawlUtils.extractCharset(recis.getContentReplayInputStream());
                cssCode = CrawlUtils.convertSourceCodeIntoUtf8(recis, charset);
            }
            RelatedContent cssContent = contentDataService.getRelatedContent(mainWebResource, curi.getURI());
            if (cssContent != null) {
                if (cssContent instanceof StylesheetContent) {
                    LOGGER.debug("Found css from related content already saved " + curi.getURI());
                    ((StylesheetContent) cssContent).setSource(cssCode);
                    saveAndPersistFetchDataToContent((Content) cssContent, curi);
                } else {
                    LOGGER.debug("Conversion From RelatedContent to Css" + curi.getURI());
                    StylesheetContent newCssContent = contentFactory.createStylesheetContent(
                            null,
                            curi.getURI(),
                            null,
                            cssCode,
                            curi.getFetchStatus());
                    cssContent = contentDataService.getRelatedContentFromUriWithParentContent(
                            mainWebResource,
                            curi.getURI());
                    newCssContent.addAllParentContent(cssContent.getParentContentSet());
                    deleteRelatedContent(cssContent);
                    saveAndPersistFetchDataToContent((Content) newCssContent, curi);
                }
            }
        } else if (curi.getContentType().contains(ContentType.img.getType())) {
            RelatedContent imgContent = contentDataService.getRelatedContent(
                    mainWebResource,
                    curi.getURI());
            if (imgContent != null) {
                if (imgContent instanceof ImageContent) {
                    LOGGER.debug("Found image from related content already saved " + curi.getURI());
                    ((ImageContent) imgContent).setContent(CrawlUtils.getImageContent(recis.getContentReplayInputStream(),
                            CrawlUtils.getImageExtension(curi.getURI())));
                    saveAndPersistFetchDataToContent((Content) imgContent, curi);
                } else {
                    LOGGER.debug("Conversion From RelatedContent to Image" + curi.getURI());
                    ImageContent newImgContent = contentFactory.createImageContent(
                            null,
                            curi.getURI(),
                            null,
                            CrawlUtils.getImageContent(recis.getContentReplayInputStream(),
                            CrawlUtils.getImageExtension(curi.getURI())),
                            curi.getFetchStatus());
                    imgContent = contentDataService.getRelatedContentFromUriWithParentContent(
                            mainWebResource,
                            curi.getURI());
                    newImgContent.addAllParentContent(imgContent.getParentContentSet());
                    deleteRelatedContent(imgContent);
                    saveAndPersistFetchDataToContent(newImgContent, curi);
                }
            }
        } else {
            RelatedContent unknownContent = contentDataService.getRelatedContentFromUriWithParentContent(
                    mainWebResource,
                    curi.getURI());
            if (unknownContent != null) {
                deleteRelatedContent(unknownContent);
            }
        }
    }

    @Override
    public void computeAndPersistUnsuccessfullFetchedResource(CrawlURI curi) {
        ContentType resourceContentType =
                getContentTypeFromUnreacheableResource(curi.getCanonicalString());
        switch (resourceContentType) {
            case css:
                LOGGER.info(
                        UNREACHABLE_RESOURCE_STR + curi.getURI() + " : "
                        + curi.getFetchStatus());
                RelatedContent relatedContent = contentDataService.getRelatedContent(
                        mainWebResource,
                        curi.getURI());
                saveAndPersistFetchDataToContent((Content) relatedContent, curi);
                break;
            case html:
                LOGGER.info(UNREACHABLE_RESOURCE_STR + curi.getURI() + " : "
                        + curi.getFetchStatus());
                WebResource wr = null;
                if (mainWebResource instanceof Site) {
                    wr =
                            webResourceDataService.getByUrlAndParentWebResource(
                            curi.getURI(),
                            mainWebResource);
                } else {
                    wr = mainWebResource;
                }
                Content htmlContent = contentDataService.findSSP(wr, curi.getURI());
                saveAndPersistFetchDataToContent(htmlContent, curi);
                break;
            case img:
                LOGGER.info(UNREACHABLE_RESOURCE_STR + curi.getURI() + " : "
                        + curi.getFetchStatus());
                relatedContent = contentDataService.getRelatedContent(
                        mainWebResource,
                        curi.getURI());
                saveAndPersistFetchDataToContent((Content) relatedContent, curi);
                break;
            case misc:
                if (mainWebResource instanceof Site) {
                    wr =
                            webResourceDataService.getByUrlAndParentWebResource(
                            curi.getURI(),
                            mainWebResource);
                } else {
                    wr = mainWebResource;
                }
                htmlContent = contentDataService.findSSP(wr, curi.getURI());
                if (htmlContent != null) {
                    saveAndPersistFetchDataToContent(htmlContent, curi);
                } else {
                    relatedContent = contentDataService.getRelatedContent(
                            mainWebResource,
                            curi.getURI());
                    if (relatedContent != null) {
                        saveAndPersistFetchDataToContent((Content) relatedContent, curi);
                    }
                }
                LOGGER.debug("MISC" + UNREACHABLE_RESOURCE_STR + curi.getURI() + " : "
                        + curi.getFetchStatus());
                break;
            default:
                LOGGER.debug("UNKNOWN_CONTENT" + UNREACHABLE_RESOURCE_STR + curi.getURI() + " : "
                        + curi.getFetchStatus());
                break;
        }
    }

    @Override
    public synchronized void computeResource(CrawlURI curi) {
        Date beginProcessDate = null;
        Date endProcessDate = null;
        Date endPersistDate = null;
        LOGGER.debug("computeResource " + curi.getURI());
        // We first create a webResource associated with the curi object heritrix is extracting outlinks from.
        Page page = null;
//        if (parentWebResource instanceof Page && ((Page)parentWebResource).getURL().equalsIgnoreCase(curi.getURI())) {
        if (mainWebResource instanceof Page) {
            if (isPageAlreadyFetched) {
                return;
            }
            // In case of audit of page type, we expect only one webresource and one SSP.
            // That's why we associate the current parentWebresource with the current curi object
            LOGGER.debug("createPage in audit page" + curi.getURI());
            page = (Page) mainWebResource;
            page = (Page) webResourceDataService.saveOrUpdate(page);
        } else if (mainWebResource instanceof Site) {
            // In case of audit of site type, we expect several webresources and SSPs.
            // These webresouces have to be linked with the parent webresource of site type.
            LOGGER.debug("createPage in audit site" + curi.getURI());
            page = webResourceFactory.createPage(curi.getURI());
            ((Site) mainWebResource).addChild(page);
//            ((Site) mainWebResource).getComponentList().clear();
            page = (Page) webResourceDataService.saveOrUpdate(page);
            ((Site) mainWebResource).getComponentList().clear();
        }
        // We create the SSP associated with Page heritrix is extracting outlinks from.
        checkURIRecordedAsRelatedContent(mainWebResource, curi.getURI());
        SSP ssp = contentFactory.createSSP(curi.getURI());
        ssp.setPage(page);
        // we persist it.
        LOGGER.debug("persist SSP " + curi.getURI());
        ssp = (SSP) contentDataService.saveOrUpdate(ssp);
        // For each oulinks found, we create the appropriate related content object
        // and link it with the current SSP. To avoid to have 2 related content
        // with the same URI, we maintain a local map. This case happens when a same
        // link is found in the same page but with a different type (E as Embed or
        // L as Link)
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Analysing outlink for  "
                    + ssp.getURI());
            beginProcessDate = Calendar.getInstance().getTime();
        }
        Set<String> localMap = new HashSet<String>();
        Set<Long> relatedContentIdSet = new HashSet<Long>();
        String linkUrl = null;
        for (Link link : curi.getOutLinks()) {
            linkUrl = link.getDestination().toString();
            if (!localMap.contains(linkUrl)) {
                Long relatedContentId = linkOutlinkWithSSP(link, ssp);
                if (relatedContentId != null) {
                    relatedContentIdSet.add(relatedContentId);
                    // In case of the related content we associate with the current
                    // SSP is associated with other related contents (a css called
                    // by another css for example), we associate all these contents
                    // with the current SSP.
                    if (relatedCssMap.containsKey(relatedContentId)) {
                        relatedContentIdSet.addAll(relatedCssMap.get(relatedContentId));
                    }
                }
            }
            localMap.add(linkUrl);
        }
        if (LOGGER.isDebugEnabled()) {
            endProcessDate = Calendar.getInstance().getTime();
            LOGGER.debug("Save SSP " + ssp.getURI() + " with "
                    + relatedContentIdSet.size()
                    + " elements. Processing took "
                    + (endProcessDate.getTime() - beginProcessDate.getTime())
                    + " ms");
            LOGGER.debug("persistedOutlinksMap contains " + persistedOutlinksMap.size());
        }
        try {
            contentDataService.saveContentRelationShip(ssp, relatedContentIdSet);
        } catch (PersistenceException e) {
            LOGGER.warn(e);
            LOGGER.warn("problem persisting  " + ssp.getURI() + "with ");
            for (Long id : relatedContentIdSet) {
                LOGGER.warn("ssp Id : " + ssp.getId() + " relatedContent Id : " + id);
            }
        }
        if (LOGGER.isDebugEnabled()) {
            endPersistDate = Calendar.getInstance().getTime();
            LOGGER.debug("Spent "
                    + (endPersistDate.getTime() - endProcessDate.getTime())
                    + " ms saving " + ssp.getURI());
        }
    }

    /**
     * In case of css extractred from another css, we combine the child
     * content with its css parents. Each CSS resource has to be associated with
     * a HTML content. By keeping this relation, we can combine a child CSS
     * with the HTML contents combined with its parents.
     * @param curi
     */
    @Override
    public synchronized void computeCSSResource(CrawlURI curi) {
        RelatedContent cssContent =
                contentDataService.getRelatedContent(
                mainWebResource,
                curi.getURI());
        if (cssContent == null) {
            return;
        }
        UURI localUuri = null;
        CrawlURI localCuri = null;
        for (Link link : curi.getOutLinks()) {
            try {
                localUuri = UURIFactory.getInstance(link.getDestination().toString());
            } catch (URIException ex) {
                LOGGER.warn(ex);
            }
            localCuri = new CrawlURI(localUuri);
            if (getDecideRuleSequence().innerDecide(localCuri).equals(DecideResult.ACCEPT)) {
                Long relatedContentId = contentDataService.getRelatedContentId(
                        mainWebResource,
                        localCuri.getURI());
                if (relatedContentId != null) {
                    if (relatedCssMap.containsKey(((Content) cssContent).getId())) {
                        relatedCssMap.get(((Content) cssContent).getId()).add(relatedContentId);
                    } else {
                        List<Long> idList = new ArrayList<Long>();
                        idList.add(relatedContentId);
                        relatedCssMap.put(((Content) cssContent).getId(), idList);
                    }
                } else {
                    RelatedContent relatedContent =
                            createRelatingContentRegardingExtension(null, localCuri);
                    contentDataService.saveOrUpdate((Content) relatedContent);
                    if (relatedCssMap.containsKey(((Content) cssContent).getId())) {
                        relatedCssMap.get(((Content) cssContent).getId()).add(((Content) relatedContent).getId());
                    } else {
                        List<Long> idList = new ArrayList<Long>();
                        idList.add(((Content) relatedContent).getId());
                        relatedCssMap.put(((Content) cssContent).getId(), idList);
                    }
                }
            }
        }
    }

    /**
     * 
     * @param link
     * @param ssp
     * @return
     */
    private Long linkOutlinkWithSSP(Link link, SSP ssp) {
        UURI localUuri = null;
        CrawlURI localCuri = null;
        try {
            localUuri = UURIFactory.getInstance(link.getDestination().toString());
        } catch (URIException ex) {
            LOGGER.warn(ex);
        }
        localCuri = new CrawlURI(localUuri);
        // We use the heritrix matchesListRegexDecideRule to filter "a priori"
        // the outlinks that will be fetched
        if (getDecideRuleSequence().innerDecide(localCuri).equals(DecideResult.ACCEPT)
                && !getHtmlFilePattern().matcher(localCuri.getURI()).matches()
                && !isUriSSP(mainWebResource, localCuri.getURI())) {
            // if the current outlink has already been encountered, a related
            // content has been created and persisted.
            String md5Uri = null;
            try {
                md5Uri = MD5Encoder.MD5(localCuri.getURI());
            } catch (NoSuchAlgorithmException ex) {
                LOGGER.warn(ex);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.warn(ex);
            }
            if (persistedOutlinksMap.containsKey(md5Uri)) {
                return persistedOutlinksMap.get(md5Uri);
            } else {
                // in case of the related content hasn't been encountered yet,
                // we create the appropriate instance regarding its extension
                // and associate it bidirectionaly with the SSP.
                RelatedContent relatedContent =
                        createRelatingContentRegardingExtension(ssp, localCuri);
                if (relatedContent != null) {
                    contentDataService.saveOrUpdate((Content) relatedContent);
                    Long id = Long.valueOf(((Content) relatedContent).getId());
                    persistedOutlinksMap.put(md5Uri, id);
                    return id;
                }
            }
        }
        return null;
    }

    /**
     * This method checks whether a resource has already been persisted as
     * a related content. If true, the related content and its relations are
     * deleted.
     * @param WebResource
     * @param uri
     */
    private void checkURIRecordedAsRelatedContent(WebResource WebResource, String uri) {
        Long id = contentDataService.getRelatedContentId(WebResource, uri);
        if (id != null) {
            LOGGER.debug("Fake related content Found with URI " + uri);
            contentDataService.delete(id);
            try {
                persistedOutlinksMap.remove(MD5Encoder.MD5(uri));
            } catch (NoSuchAlgorithmException ex) {
                LOGGER.warn(ex);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.warn(ex);
            }
        }
    }

    /**
     * 
     * @param relatedContent
     */
    private void deleteRelatedContent(RelatedContent relatedContent) {
        LOGGER.debug("Deleting " + ((Content) relatedContent).getURI());
        contentDataService.delete(((Content) relatedContent).getId());
        try {
            persistedOutlinksMap.remove(MD5Encoder.MD5(((Content) relatedContent).getURI()));
        } catch (NoSuchAlgorithmException ex) {
            LOGGER.warn(ex);
        } catch (UnsupportedEncodingException ex) {
            LOGGER.warn(ex);
        }
    }

    /**
     * This methods checks whether a resource has already been persisted as
     * a SSP.
     * @param webResource
     * @param uri
     * @return
     */
    private boolean isUriSSP(WebResource webResource, String uri) {
        return contentDataService.checkSSPExist(uri, webResource);
    }

    /**
     * 
     * @param ssp
     * @param uri
     * @return
     */
    private RelatedContent createRelatingContentRegardingExtension(SSP ssp, CrawlURI uri) {
        ContentType contentType = getContentTypeFromUnreacheableResource(uri.getURI());
        switch (contentType) {
            case css:
                return contentFactory.createStylesheetContent(uri.getURI(), ssp);
            case img:
                return contentFactory.createImageContent(uri.getURI(), ssp);
            case html:
                return null;
            case misc:
            default:
                return contentFactory.createRelatedContent(uri.getURI(), ssp);
        }
    }

    /**
     * This methods enables to get the type of resource from its uri.
     * In case of unreachable resource (404/403 errors), the return content is
     * a html page. So we can't use the content type of the returned page to
     * determine the type of the content we try to reach. In this case, we use
     * the uri extension, based-on regular expressions.
     * @param uri
     * @return
     */
    private ContentType getContentTypeFromUnreacheableResource(String uri) {
        if (MatchesFilePatternDecideRule.Preset.IMAGES.getPattern().
                matcher(uri).matches()) {
            return ContentType.img;
        } else if (getHtmlFilePattern().matcher(uri).matches()) {
            return ContentType.html;
        } else if (getCssFilePattern().matcher(uri).matches()) {
            return ContentType.css;
        }
        return ContentType.misc;
    }

    // Bug #154 fix
    /**
     * Some resources may have been downloaded by the crawler component but they
     * are not linked with any webresource. They have to be removed from the
     * contentList.
     */
    @SuppressWarnings("element-type-mismatch")
    private void removeOrphanContent() {
        List<Content> emptyContentSet = null;
        Integer nbOfContent = contentDataService.getNumberOfOrphanRelatedContent(mainWebResource).intValue();
        Integer i = 0;
        Logger.getLogger(CrawlerImpl.class.getName()).debug("remove Orphan related contents  " + nbOfContent + " elements");
        while (i.compareTo(nbOfContent) < 0) {
            emptyContentSet = contentDataService.getOrphanRelatedContentList(mainWebResource, 0, RETRIEVE_WINDOW);
            for (Content content : emptyContentSet) {
                Logger.getLogger(CrawlerImpl.class.getName()).debug("Removing " + content.getURI());
                contentDataService.delete(content.getId());
            }
            i = i + RETRIEVE_WINDOW;
        }

        nbOfContent = contentDataService.getNumberOfOrphanContent(mainWebResource).intValue();
        i = 0;
        Logger.getLogger(CrawlerImpl.class.getName()).debug("remove Orphan SSPs  " + nbOfContent + " elements");
        while (i.compareTo(nbOfContent) < 0) {
            emptyContentSet = contentDataService.getOrphanContentList(mainWebResource, i, RETRIEVE_WINDOW);
            for (Content content : emptyContentSet) {
                contentDataService.delete(content.getId());
            }
            i = i + RETRIEVE_WINDOW;
        }
    }

    /**
     * This methods add the fetch date and the fetch status to a content and
     * persist it
     * @param content
     * @param curi
     */
    private void saveAndPersistFetchDataToContent(Content content, CrawlURI curi) {
        content.setHttpStatusCode(curi.getFetchStatus());
        content.setDateOfLoading(new Date(curi.getFetchCompletedTime()));
        contentDataService.saveOrUpdate(content);
    }
}
