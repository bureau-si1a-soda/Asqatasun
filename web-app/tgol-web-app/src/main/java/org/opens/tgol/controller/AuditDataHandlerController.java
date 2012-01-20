/*
 * Tanaguru - Automated webpage assessment
 * Copyright (C) 2008-2011  Open-S Company
 *
 * This file is part of Tanaguru.
 *
 * Tanaguru is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact us by mail: open-s AT open-s DOT com
 */
package org.opens.tgol.controller;

import org.opens.tgol.entity.contract.Act;
import org.opens.tgol.entity.contract.Contract;
import org.opens.tgol.entity.decorator.tanaguru.parameterization.ParameterDataServiceDecorator;
import org.opens.tgol.entity.decorator.tanaguru.subject.WebResourceDataServiceDecorator;
import org.opens.tgol.entity.service.contract.ActDataService;
import org.opens.tgol.entity.user.User;
import org.opens.tgol.presentation.data.AuditStatistics;
import org.opens.tgol.presentation.factory.AuditStatisticsFactory;
import org.opens.tgol.util.HttpStatusCodeFamily;
import org.opens.tgol.util.TgolKeyStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.persistence.NoResultException;
import javax.servlet.http.HttpServletRequest;
import org.displaytag.pagination.PaginatedList;
import org.opens.tanaguru.entity.audit.Audit;
import org.opens.tanaguru.entity.audit.AuditStatus;
import org.opens.tanaguru.entity.parameterization.Parameter;
import org.opens.tanaguru.entity.reference.Scope;
import org.opens.tanaguru.entity.service.audit.ContentDataService;
import org.opens.tanaguru.entity.service.parameterization.ParameterDataService;
import org.opens.tanaguru.entity.service.reference.ScopeDataService;
import org.opens.tanaguru.entity.service.reference.TestDataService;
import org.opens.tanaguru.entity.subject.Page;
import org.opens.tanaguru.entity.subject.Site;
import org.opens.tanaguru.entity.subject.WebResource;
import org.opens.tgol.exception.ForbiddenPageException;
import org.opens.tgol.exception.ForbiddenUserException;
import org.opens.tgol.report.pagination.factory.TgolPaginatedListFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.servlet.LocaleResolver;


/**
 * This abstract controller handles methods to retrieve and format audit data
 * @author jkowalczyk
 */
@Controller
public abstract class AuditDataHandlerController extends AbstractController {

    private int pageScopeId = 1;
    public void setPageScopeId(int pageScopeId) {
        this.pageScopeId = pageScopeId;
    }

    private int siteScopeId = 2;
    public void setSiteScopeId(int siteScopeId) {
        this.siteScopeId = siteScopeId;
    }

    private Scope siteScope;
    /**
     *
     * @return the scope instance
     */
    public Scope getSiteScope() {
        return siteScope;
    }

    private Scope pageScope;
    public Scope getPageScope() {
        return pageScope;
    }

    /*
     * Displaying bounds
     */
    protected static final String FROM_VALUE = "fromValue";
    protected static final String TO_VALUE = "toValue";

    /*
     * Authorized elements depending on the context.
     */
    private Set<Integer> authorizedPageSize = new LinkedHashSet<Integer>();
    public Set<Integer> getAuthorizedPageSize() {
        return authorizedPageSize;
    }
    
    public final void setAuthorizedPageSizeList(Set<String> authorizedPageSizeList) {
        for (String size : authorizedPageSizeList) {
            this.authorizedPageSize.add(Integer.valueOf(size));
        }
    }

    private final Set<String> authorizedSortCriterion = new LinkedHashSet<String>();
    public Set<String> getAuthorizedSortCriterion() {
        return authorizedSortCriterion;
    }

    /**
     * This method initializes the siteScope and the pageScope instances through
     * their persistence Id.
     * @param scopeDataService
     */
    @Autowired
    public final void setScopeDataService(ScopeDataService scopeDataService) {
        siteScope = scopeDataService.read(Long.valueOf(siteScopeId));
        pageScope = scopeDataService.read(Long.valueOf(pageScopeId));
    }

    private WebResourceDataServiceDecorator webResourceDataService;
    public WebResourceDataServiceDecorator getWebResourceDataService() {
        return webResourceDataService;
    }

    @Autowired
    public final void setWebResourceDataService(WebResourceDataServiceDecorator webResourceDataService) {
        this.webResourceDataService = webResourceDataService;
    }

    private ActDataService actDataService;
    public ActDataService getActDataService() {
        return actDataService;
    }

    @Autowired
    public final void setActDataService(ActDataService actDataService) {
        this.actDataService = actDataService;
    }

    private ContentDataService contentDataService;
    public ContentDataService getContentDataService() {
        return contentDataService;
    }

    @Autowired
    public final void setContentDataService(ContentDataService contentDataService) {
        this.contentDataService = contentDataService;
    }

    private TestDataService testDataService;
    public TestDataService getTestDataService() {
        return testDataService;
    }

    @Autowired
    public final void setTestDataService(TestDataService testDataService) {
        this.testDataService = testDataService;
    }

    private ParameterDataServiceDecorator parameterDataService;
    public ParameterDataServiceDecorator getParameterDataService() {
        return parameterDataService;
    }

    @Autowired
    public final void setParameterDataService(ParameterDataServiceDecorator parameterDataService) {
        this.parameterDataService = parameterDataService;
        // the audit Set up factory needs to be initialised with the unique instance
        // of ParameterDataServiceDecorator
        setDefaultParamSet(parameterDataService);
    }

    private Set<Parameter> defaultParamSet;
    public Set<Parameter> getDefaultParamSet() {
        return ((Set) ((HashSet) defaultParamSet).clone());
    }

    public final void setDefaultParamSet(ParameterDataService parameterDataService) {
        this.defaultParamSet = parameterDataService.getDefaultParameterSet();
    }

    private Map<String, String> parametersToDisplay = new LinkedHashMap<String, String>();
    public Map<String, String> getParametersToDisplay() {
        return parametersToDisplay;
    }

    public void setParametersToDisplay(Map<String, String> parametersToDisplay) {
        this.parametersToDisplay.putAll(parametersToDisplay);
    }

    private LocaleResolver localeResolver;
    public LocaleResolver getLocaleResolver() {
        return localeResolver;
    }

    private List<String> authorizedScopeForPageList = new ArrayList<String>();
    public void setAuthorizedScopeForPageList(List<String> authorizedScopeForPageList) {
        this.authorizedScopeForPageList = authorizedScopeForPageList;
    }

    public List<String> getAuthorizedScopeForPageList() {
        return authorizedScopeForPageList;
    }

    protected boolean isAuthorizedScopeForPageList(WebResource webResource) {
        if (webResource instanceof Page) {
            return false;
        }
        String scope = getActDataService().getActFromWebResource(webResource).getScope().getCode().name();
        return (authorizedScopeForPageList.contains(scope))? true : false;
    }

    @Autowired
    public final void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    public AuditDataHandlerController() {}

    /**
     * Add a populated auditStatistics instance to the model
     * 
     * @param webResource
     * @param model
     * @param hasResultAction
     */
    protected void addAuditStatisticsToModel(WebResource webResource, Model model) {
        model.addAttribute(
                TgolKeyStore.STATISTICS_KEY,
                getAuditStatistics(webResource, model));
    }

    /**
     * 
     * @param webResource
     * @param model
     * @param hasResultAction
     * @return
     */
    protected AuditStatistics getAuditStatistics(WebResource webResource, Model model){
        return AuditStatisticsFactory.getInstance().getAuditStatistics(webResource, getParametersToDisplay());
    }

    /**
     * This methods checks whether the current user is allowed to display the
     * audit result of a given webresource. To do so, we verify that the act
     * associated with the audited webresource belongs to the current user and
     * that the current contract is not expired
     * @param user
     * @param webresourceId
     * @return
     *      true if the user is allowed to display the result, false otherwise.
     */
    protected boolean isUserAllowedToDisplayResult(User user, WebResource webResource) {
        if (webResource == null) {
            throw new ForbiddenPageException(getCurrentUser());
        }
        if (user == null) {
            throw new ForbiddenUserException(getCurrentUser());
        }
        try {
            Act act= actDataService.getActFromWebResource(webResource);
            if (!isContractExpired(act.getContract()) && user.getId().compareTo(
                    act.getContract().getUser().getId()) == 0) {
                return true;
            }
            throw new ForbiddenUserException(getCurrentUser());
        } catch (NoResultException e) {
            if (webResource.getParent() != null) {
                Act act= actDataService.getActFromWebResource(webResource.getParent());
                if (!isContractExpired(act.getContract()) && user.getId().compareTo(
                    act.getContract().getUser().getId()) == 0) {
                    return true;
                }
            }
            throw new ForbiddenUserException(getCurrentUser());
        }
    }

    /**
     * This methods checks whether the current user is allowed to display the
     * audit set-up for a given contract. To do so, we verify that the contract
     * belongs to the current user.
     * 
     * @param user
     * @param webresourceId
     * @return
     *      true if the user is allowed to display the result, false otherwise.
     */
    protected boolean isUserAllowedToDisplaySetUpPage(Contract contract) {
        if (contract == null) {
            throw new ForbiddenUserException(getCurrentUser());
        }
        User user = getCurrentUser();
        if (!contract.getUser().getId().equals(user.getId())) {
            throw new ForbiddenUserException(getCurrentUser());
        }
        return true;
    }

    /**
     * @param webResourceId
     * @return The Contract associated with the given WebResource (through the 
     * Act associated with the given WebResource.
     *
     */
    protected Contract retrieveContractFromWebResource(WebResource webResource) {
        Act act = null;
        try {
            act = getActDataService().getActFromWebResource(webResource);
        } catch (NoResultException e) {
            if (webResource!=null && webResource.getParent() != null) {
                act = getActDataService().getActFromWebResource(webResource.getParent());
            }
        }
        if (act!= null && act.getContract() != null) {
            return act.getContract();
        }
        return null;
    }

    /**
     * 
     * @param webResource
     * @return
     */
    protected String computeAuditStatus(Audit audit) {
        if (audit.getStatus().equals(AuditStatus.COMPLETED)) {
            return TgolKeyStore.COMPLETED_KEY;
        } else if (!contentDataService.hasContent(audit)) {
            return TgolKeyStore.ERROR_LOADING_KEY;
        } else if (!contentDataService.hasAdaptedSSP(audit)) {
            return TgolKeyStore.ERROR_ADAPTING_KEY;
        } else {
            return TgolKeyStore.ERROR_UNKNOWN_KEY;
        }
    }

    /**
     * This method determines which page to display when an error occured
     * while processing
     * @param audit
     * @param model
     * @param contract
     * @return
     */
    protected String prepareFailedAuditData(Audit audit, Model model) {
        String returnViewName = TgolKeyStore.OUPS_VIEW_NAME;
        model.addAttribute(TgolKeyStore.AUDIT_URL_KEY,
                audit.getSubject().getURL());
        model.addAttribute(TgolKeyStore.AUDIT_DATE_KEY,
                audit.getDateOfCreation());
        String status = this.computeAuditStatus(audit);
        if (status.equalsIgnoreCase(TgolKeyStore.ERROR_LOADING_KEY)) {
            returnViewName = TgolKeyStore.LOADING_ERROR_VIEW_NAME;
        } else if (status.equalsIgnoreCase(TgolKeyStore.ERROR_ADAPTING_KEY)) {
            returnViewName = TgolKeyStore.ADAPTING_ERROR_VIEW_NAME;
        }
        return returnViewName;
    }

    /**
     *
     * @param site
     * @param model
     * @return
     */
    protected String preparePageListStatsByHttpStatusCode(
            Site site,
            Model model,
            HttpStatusCodeFamily httpStatusCode,
            HttpServletRequest request,
            boolean returnRedirectView) throws ServletRequestBindingException {

        PaginatedList paginatedList = TgolPaginatedListFactory.getInstance().getPaginatedList(
                httpStatusCode,
                ServletRequestUtils.getStringParameter(request, TgolPaginatedListFactory.PAGE_SIZE_PARAM),
                ServletRequestUtils.getStringParameter(request, TgolPaginatedListFactory.SORT_DIRECTION_PARAM),
                ServletRequestUtils.getStringParameter(request, TgolPaginatedListFactory.SORT_CRITERION_PARAM),
                ServletRequestUtils.getStringParameter(request, TgolPaginatedListFactory.PAGE_PARAM),
                ServletRequestUtils.getStringParameter(request, TgolPaginatedListFactory.SORT_CONTAINING_URL_PARAM),
                authorizedPageSize,
                authorizedSortCriterion,
                site.getAudit().getId());

        model.addAttribute(TgolKeyStore.PAGE_LIST_KEY, paginatedList);
        model.addAttribute(TgolKeyStore.AUTHORIZED_PAGE_SIZE_KEY, authorizedPageSize);
        model.addAttribute(TgolKeyStore.AUTHORIZED_SORT_CRITERION_KEY, authorizedSortCriterion);
        setFromToValues(paginatedList, model);
        return (returnRedirectView) ? TgolKeyStore.PAGE_LIST_XXX_VIEW_REDIRECT_NAME : TgolKeyStore.PAGE_LIST_XXX_VIEW_NAME;
    }

    /**
     *
     * @param pageResultList
     * @param model
     * @return
     */
    private void setFromToValues(PaginatedList pageResultList, Model model) {
        model.addAttribute(FROM_VALUE,
                (pageResultList.getPageNumber()-1) * pageResultList.getObjectsPerPage() +1);
        if (pageResultList.getList().size() < pageResultList.getObjectsPerPage()) {
            model.addAttribute(TO_VALUE,
                    (pageResultList.getPageNumber()-1) * pageResultList.getObjectsPerPage() + pageResultList.getList().size());
        } else {
            model.addAttribute(TO_VALUE,
                    (pageResultList.getPageNumber()) * pageResultList.getObjectsPerPage());
        }
    }

    
}