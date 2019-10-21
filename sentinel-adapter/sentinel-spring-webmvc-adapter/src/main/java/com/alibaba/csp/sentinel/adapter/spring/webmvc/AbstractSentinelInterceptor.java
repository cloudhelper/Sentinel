/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.adapter.spring.webmvc;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.adapter.spring.webmvc.config.BaseWebMvcConfig;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * @Author kaizi2009
 */
public abstract class AbstractSentinelInterceptor implements HandlerInterceptor {

    public static final String SPRING_MVC_CONTEXT_NAME = "spring_mvc_context";
    private static final String EMPTY_ORIGIN = "";
    protected static final String COLON = ":";
    private BaseWebMvcConfig baseWebMvcConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Entry urlEntry = null;
        Entry httpMethodUrlEntry = null;

        try {
            String resourceName = getResourceName(request);

            if (!StringUtil.isEmpty(resourceName)) {
                // Parse the request origin using registered origin parser.
                String origin = parseOrigin(request);
                ContextUtil.enter(SPRING_MVC_CONTEXT_NAME, origin);
                urlEntry = SphU.entry(resourceName, EntryType.IN);
                // Add method specification if necessary
                if (baseWebMvcConfig.isHttpMethodSpecify()) {
                    httpMethodUrlEntry = SphU.entry(request.getMethod().toUpperCase() + COLON + resourceName,
                            EntryType.IN);
                }
                final EntryContainer entryContainer = new EntryContainer().setUrlEntry(urlEntry)
                        .setHttpMethodUrlEntry(httpMethodUrlEntry);
                setEntryContainerInReqeust(request, baseWebMvcConfig.getRequestAttributeName(), entryContainer);
            }
            return true;
        } catch (BlockException e) {
            handleBlockException(request, response, e);
            return false;
        }
    }

    /**
     * Get sentinel resource name.
     * @param request
     * @return
     */
    protected abstract String getResourceName(HttpServletRequest request);

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        EntryContainer entryContainer = getEntryContainerInReqeust(request, baseWebMvcConfig.getRequestAttributeName());
        if (entryContainer != null) {
            traceExceptionAndExit(entryContainer.getHttpMethodUrlEntry(), ex);
            traceExceptionAndExit(entryContainer.getUrlEntry(), ex);
            removeEntryContainerInReqeust(request);
        }
        ContextUtil.exit();
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
    }

    protected void setEntryContainerInReqeust(HttpServletRequest request, String name, EntryContainer entryContainer) {
        Object attrVal = request.getAttribute(name);
        if (attrVal != null) {
            RecordLog.warn(String.format("Already exist attribute name '%s' in request, please set `requestAttributeName`", name));
        } else {
            request.setAttribute(name, entryContainer);
        }
    }

    protected EntryContainer getEntryContainerInReqeust(HttpServletRequest request, String attrKey) {
        Object entityContainerObject = request.getAttribute(attrKey);
        return entityContainerObject == null ? null : (EntryContainer) entityContainerObject;
    }

    protected void removeEntryContainerInReqeust(HttpServletRequest request) {
        request.removeAttribute(baseWebMvcConfig.getRequestAttributeName());
    }

    protected void traceExceptionAndExit(Entry entry, Exception ex) {
        if (entry != null) {
            if (ex != null) {
                Tracer.traceEntry(ex, entry);
            }
            entry.exit();
        }
    }

    protected void handleBlockException(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception {
        if (baseWebMvcConfig.getBlockExceptionHandler() != null) {
            baseWebMvcConfig.getBlockExceptionHandler().handle(request, response, e);
        } else {
            //Throw BlockException, handle it in spring mvc
            throw e;
        }
    }

    protected String parseOrigin(HttpServletRequest request) {
        String origin = EMPTY_ORIGIN;
        if (baseWebMvcConfig.getOriginParser() != null) {
            origin = baseWebMvcConfig.getOriginParser().parseOrigin(request);
            if (StringUtil.isEmpty(origin)) {
                return EMPTY_ORIGIN;
            }
        }
        return origin;
    }

    protected void setBaseWebMvcConfig(BaseWebMvcConfig config) {
        this.baseWebMvcConfig = config;
    }
}
