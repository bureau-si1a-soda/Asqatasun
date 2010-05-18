/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.opens.tanaguru.rule.accessiweb20;

import org.opens.tanaguru.entity.audit.TestSolution;
import org.opens.tanaguru.rule.test.AbstractRuleImplementationTestCase;

/**
 *
 * @author jkowalczyk
 */
public class Aw20Rule09011Test extends AbstractRuleImplementationTestCase {
    
    public Aw20Rule09011Test(String testName) {
        super(testName);
    }

    @Override
    protected void setUpRuleImplementationClassName() {
        ruleImplementationClassName =
                "org.opens.tanaguru.rule.accessiweb20.Aw20Rule09011";
    }

    @Override
    protected void setUpWebResourceMap() {
        webResourceMap.put("AW20.Test.09.01.01-1Passed-01",
                webResourceFactory.createPage(
                testcasesFilePath + "AW22/AW20.Test.09.01.01-1Passed-01.html"));
        webResourceMap.put("AW20.Test.09.01.01-1Passed-02",
                webResourceFactory.createPage(
                testcasesFilePath + "AW22/AW20.Test.09.01.01-1Passed-02.html"));
        webResourceMap.put("AW20.Test.09.01.01-2Failed-01",
                webResourceFactory.createPage(
                testcasesFilePath + "AW22/AW20.Test.09.01.01-2Failed-01.html"));
        webResourceMap.put("AW20.Test.09.01.01-2Failed-02",
                webResourceFactory.createPage(
                testcasesFilePath + "AW22/AW20.Test.09.01.01-2Failed-02.html"));
    }

    @Override
    protected void setProcess() {
        assertEquals(TestSolution.PASSED,
                processPageTest("AW20.Test.09.01.01-1Passed-01").getValue());
        assertEquals(TestSolution.PASSED,
                processPageTest("AW20.Test.09.01.01-1Passed-02").getValue());
        assertEquals(TestSolution.FAILED,
                processPageTest("AW20.Test.09.01.01-2Failed-01").getValue());
        assertEquals(TestSolution.FAILED,
                processPageTest("AW20.Test.09.01.01-2Failed-02").getValue());
    }

    @Override
    protected void setConsolidate() {
        assertEquals(TestSolution.PASSED,
                consolidate("AW20.Test.09.01.01-1Passed-01").getValue());
        assertEquals(TestSolution.PASSED,
                consolidate("AW20.Test.09.01.01-1Passed-02").getValue());
        assertEquals(TestSolution.FAILED,
                consolidate("AW20.Test.09.01.01-2Failed-01").getValue());
        assertEquals(TestSolution.FAILED,
                consolidate("AW20.Test.09.01.01-2Failed-02").getValue());
    }

}
