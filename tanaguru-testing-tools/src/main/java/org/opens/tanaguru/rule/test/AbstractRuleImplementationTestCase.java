/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opens.tanaguru.rule.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.opens.tanaguru.entity.audit.Content;
import org.opens.tanaguru.entity.audit.ProcessResult;
import org.opens.tanaguru.entity.factory.reference.TestFactory;
import org.opens.tanaguru.entity.factory.subject.WebResourceFactory;
import org.opens.tanaguru.entity.reference.Test;
import org.opens.tanaguru.entity.subject.Page;
import org.opens.tanaguru.entity.subject.Site;
import org.opens.tanaguru.entity.subject.WebResource;
import org.opens.tanaguru.service.ConsolidatorService;
import org.opens.tanaguru.service.ContentAdapterService;
import org.opens.tanaguru.service.ContentLoaderService;
import org.opens.tanaguru.service.ProcessorService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 *
 * @author lralambomanana
 */
public abstract class AbstractRuleImplementationTestCase extends TestCase {

    private String applicationContextFilePath =
            "../tanaguru-testing-tools/src/main/resources/application-context.xml";
    private BeanFactory springBeanFactory;
    private TestFactory testFactory;
    private ContentLoaderService contentLoaderService;
    private ContentAdapterService contentAdapterService;
    private ProcessorService processorService;
    private ConsolidatorService consolidatorService;
    private Map<WebResource, List<Content>> contentMap = new HashMap<WebResource, List<Content>>();
    private List<Test> testList = new ArrayList<Test>();
    private Map<WebResource, List<ProcessResult>> grossResultMap = new HashMap<WebResource, List<ProcessResult>>();
    private Map<WebResource, ProcessResult> netResultMap = new HashMap<WebResource, ProcessResult>();
    protected WebResourceFactory webResourceFactory;
    protected String ruleImplementationClassName;
    protected Map<String, WebResource> webResourceMap = new HashMap<String, WebResource>();
    protected String testcasesFilePath = "../tanaguru-testing-tools/resources/testcases/";

    public AbstractRuleImplementationTestCase(String testName) {
        super(testName);
        initialize();
        setUpRuleImplementationClassName();
        setUpWebResourceMap();
        setUpClass();
    }

    private void initialize() {
        initializePath();
        springBeanFactory = new FileSystemXmlApplicationContext(applicationContextFilePath);
        webResourceFactory = (WebResourceFactory) springBeanFactory.getBean("webResourceFactory");

        testFactory = (TestFactory) springBeanFactory.getBean("testFactory");

        contentLoaderService = (ContentLoaderService) springBeanFactory.getBean("contentLoaderService");
        contentAdapterService = (ContentAdapterService) springBeanFactory.getBean("contentAdapterService");

        processorService = (ProcessorService) springBeanFactory.getBean("processorService");

        consolidatorService = (ConsolidatorService) springBeanFactory.getBean("consolidatorService");

    }

    /**
     * In this method, set the class name of the rule implementation to test.
     */
    protected abstract void setUpRuleImplementationClassName();

    /**
     * In this method, set the web resource list to test.
     */
    protected abstract void setUpWebResourceMap();

    private void setUpClass() {
        Test test = testFactory.create();
        test.setCode(this.getName());
        test.setRuleClassName(ruleImplementationClassName);
        testList.add(test);

        for (WebResource webResource : webResourceMap.values()) {
            contentMap.put(webResource, contentLoaderService.loadContent(webResource));
            contentMap.put(webResource, contentAdapterService.adaptContent(contentMap.get(webResource)));
        }
    }

    protected List<ProcessResult> process(String webResourceKey) {
        System.out.println(this + "::process(\"" + webResourceKey + "\")");
        WebResource webResource = webResourceMap.get(webResourceKey);
        List<ProcessResult> grossResultList = processorService.process(contentMap.get(webResource), testList);
        grossResultMap.put(webResource, grossResultList);
        return grossResultList;
    }

    protected ProcessResult processPageTest(String webResourceKey) {
        return process(webResourceKey).get(0);
    }

    public ProcessResult consolidate(String webResourceKey) {
        System.out.println(this + "::consolidate(\"" + webResourceKey + "\")");
        WebResource webResource = webResourceMap.get(webResourceKey);
        ProcessResult netResult = consolidatorService.consolidate(grossResultMap.get(webResource), testList).get(0);
        netResultMap.put(webResource, netResult);
        return netResult;
    }

    public void testRuleImplementation() {
        setProcess();
        setConsolidate();
    }

    protected abstract void setProcess();

    protected abstract void setConsolidate();

    protected ProcessResult getGrossResult(String pageKey, String siteKey) {
        Site site = (Site) webResourceMap.get(siteKey);
        List<ProcessResult> grossResultList = grossResultMap.get(site);
        Page page = (Page) webResourceMap.get(pageKey);
        for (ProcessResult grossResult : grossResultList) {
            if (grossResult.getSubject().equals(page)) {
                return grossResult;
            }
        }
        return null;
    }

    private void initializePath(){
        testcasesFilePath = 
                "file://"+System.getenv("PWD")+"/"+testcasesFilePath;
        applicationContextFilePath =
                "file://"+System.getenv("PWD")+"/"+applicationContextFilePath;
    }
}
