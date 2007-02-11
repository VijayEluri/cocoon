/*

 ============================================================================
                   The Apache Software License, Version 1.1
 ============================================================================

 Copyright (C) 1999-2003 The Apache Software Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without modifica-
 tion, are permitted provided that the following conditions are met:

 1. Redistributions of  source code must  retain the above copyright  notice,
    this list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. The end-user documentation included with the redistribution, if any, must
    include  the following  acknowledgment:  "This product includes  software
    developed  by the  Apache Software Foundation  (http://www.apache.org/)."
    Alternately, this  acknowledgment may  appear in the software itself,  if
    and wherever such third-party acknowledgments normally appear.

 4. The names "Apache Cocoon" and  "Apache Software Foundation" must  not  be
    used to  endorse or promote  products derived from  this software without
    prior written permission. For written permission, please contact
    apache@apache.org.

 5. Products  derived from this software may not  be called "Apache", nor may
    "Apache" appear  in their name,  without prior written permission  of the
    Apache Software Foundation.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 FITNESS  FOR A PARTICULAR  PURPOSE ARE  DISCLAIMED.  IN NO  EVENT SHALL  THE
 APACHE SOFTWARE  FOUNDATION  OR ITS CONTRIBUTORS  BE LIABLE FOR  ANY DIRECT,
 INDIRECT, INCIDENTAL, SPECIAL,  EXEMPLARY, OR CONSEQUENTIAL  DAMAGES (INCLU-
 DING, BUT NOT LIMITED TO, PROCUREMENT  OF SUBSTITUTE GOODS OR SERVICES; LOSS
 OF USE, DATA, OR  PROFITS; OR BUSINESS  INTERRUPTION)  HOWEVER CAUSED AND ON
 ANY  THEORY OF LIABILITY,  WHETHER  IN CONTRACT,  STRICT LIABILITY,  OR TORT
 (INCLUDING  NEGLIGENCE OR  OTHERWISE) ARISING IN  ANY WAY OUT OF THE  USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 This software  consists of voluntary contributions made  by many individuals
 on  behalf of the Apache Software  Foundation and was  originally created by
 Stefano Mazzocchi  <stefano@apache.org>. For more  information on the Apache
 Software Foundation, please see <http://www.apache.org/>.

*/
package org.apache.cocoon.portal.acting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.SAXConfigurationHandler;
import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameterizable;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.acting.ServiceableAction;
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.Session;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.portal.PortalManager;
import org.apache.cocoon.portal.PortalService;
import org.apache.cocoon.portal.acting.helpers.CopletMapping;
import org.apache.cocoon.portal.acting.helpers.LayoutMapping;
import org.apache.cocoon.portal.acting.helpers.Mapping;
import org.apache.excalibur.source.Source;
import org.xml.sax.SAXException;

/**
 * This action helps you in creating bookmarks
 * 
 * The definition file is:
 * <bookmarks>
 * <events>
 *   <event type="jxpath" id="ID">
 *     <targetid>tagetId</targetid>
 *     <targettype>layout|coplet</targettype>
 *     <path/>
 *   </event>
 * </events>
 * </bookmarks>
 *
 * @author <a href="mailto:cziegeler@apache.org">Carsten Ziegeler</a>
 * @version CVS $Id: BookmarkAction.java,v 1.4 2003/12/15 13:24:51 cziegeler Exp $
*/
public class BookmarkAction
extends ServiceableAction
implements ThreadSafe, Parameterizable {

    protected Map eventMap = new HashMap();
    
    protected String historyParameterName;
    
    /* (non-Javadoc)
     * @see org.apache.avalon.framework.parameters.Parameterizable#parameterize(org.apache.avalon.framework.parameters.Parameters)
     */
    public void parameterize(Parameters parameters) throws ParameterException {
        this.historyParameterName = parameters.getParameter("history-parameter-name", "history");
        final String configurationFile = parameters.getParameter("src", null);
        if ( configurationFile == null ) return;
       
        Configuration config;
        org.apache.excalibur.source.SourceResolver resolver = null;
        try {
            resolver = (org.apache.excalibur.source.SourceResolver) this.manager.lookup(org.apache.excalibur.source.SourceResolver.ROLE);
            Source source = null;
            try {
                source = resolver.resolveURI(configurationFile);
                SAXConfigurationHandler handler = new SAXConfigurationHandler();
                SourceUtil.toSAX(source, handler);
                config = handler.getConfiguration();
            } catch (ProcessingException se) {
                throw new ParameterException("Unable to read configuration from " + configurationFile, se);
            } catch (SAXException se) {
                throw new ParameterException("Unable to read configuration from " + configurationFile, se);
            } catch (IOException ioe) {
                throw new ParameterException("Unable to read configuration from " + configurationFile, ioe);
            } finally {
                resolver.release(source);
            }
        } catch (ServiceException se) {
            throw new ParameterException("Unable to lookup source resolver.", se);
        } finally {
            this.manager.release(resolver);
        }
        Configuration[] events = config.getChild("events").getChildren("event");
        if ( events != null ) {
            for(int i=0; i<events.length;i++) {
                try {
                    final String type = events[i].getAttribute("type");
                    final String id = events[i].getAttribute("id");
                    if ( !"jxpath".equals(type) ) {
                        throw new ParameterException("Unknown event type for event " + id + ": " + type);
                    }
                    if ( this.eventMap.containsKey(id)) {
                        throw new ParameterException("The id for the event " + id + " is not unique.");
                    }
                    final String targetType = events[i].getChild("targettype").getValue();
                    final String targetId = events[i].getChild("targetid").getValue();
                    final String path = events[i].getChild("path").getValue();
                    if ( "layout".equals(targetType) ) {
                        LayoutMapping mapping = new LayoutMapping();
                        mapping.layoutId = targetId;
                        mapping.path = path;
                        this.eventMap.put(id, mapping);
                    } else if ( "coplet".equals(targetType) ) {
                        CopletMapping mapping = new CopletMapping();
                        mapping.copletId = targetId;
                        mapping.path = path;  
                        this.eventMap.put(id, mapping);
                    } else {
                       throw new ParameterException("Unknown target type " + targetType);
                    }
                } catch (ConfigurationException ce) {
                    throw new ParameterException("Configuration exception" ,ce);
                }
            }
        }
    }

    public Map act(Redirector redirector,
                   SourceResolver resolver,
                   Map objectModel,
                   String source,
                   Parameters par)
    throws Exception {
        if (this.getLogger().isDebugEnabled() ) {
            this.getLogger().debug("BEGIN act resolver="+resolver+
                                   ", objectModel="+objectModel+
                                   ", source="+source+
                                   ", par="+par);
        }

        Map result;
        PortalService service = null;
        try {
            service = (PortalService)this.manager.lookup(PortalService.ROLE);
            service.setPortalName(par.getParameter("portal-name"));

            PortalManager portalManager = null;
            try {
                portalManager = (PortalManager) this.manager.lookup(PortalManager.ROLE);
                portalManager.process();
            } finally {
                this.manager.release(portalManager);
            }
            
            final Request request = ObjectModelHelper.getRequest(objectModel);
            final Session session = request.getSession(false);
            final List events = new ArrayList();
            
            // is the history invoked?
            final String historyValue = request.getParameter(this.historyParameterName);
            if ( historyValue != null && session != null) {
                // get the history
                final List history = (List)session.getAttribute("portal-history");
                if ( history != null ) {
                    final int index = Integer.parseInt(historyValue);
                    final List state = (List)history.get(index);
                    if ( state != null ) {
                        final Iterator iter = state.iterator();
                        while ( iter.hasNext() ) {
                            Mapping m = (Mapping)iter.next();
                            events.add(m.getEvent(service, null));
                        }
                        while (history.size() > index ) {
                            history.remove(history.size()-1);
                        }
                    }
                }
            }
            Enumeration enum = request.getParameterNames();
            while (enum.hasMoreElements()) {
                String name = (String)enum.nextElement();
                String value = request.getParameter(name);
                
                Mapping m = (Mapping) this.eventMap.get(name);
                if ( m != null ) {
                    events.add(m.getEvent(service, value));
                }                
            }
            String uri = service.getComponentManager().getLinkService().getLinkURI(events);
            result = new HashMap();
            result.put("uri", uri.substring(uri.indexOf('?')+1));

        } catch (ParameterException pe) {
            throw new ProcessingException("Parameter portal-name is required.");
        } catch (ServiceException ce) {
            throw new ProcessingException("Unable to lookup portal service.", ce);
        } finally {
            this.manager.release(service);
        }

        if (this.getLogger().isDebugEnabled() ) {
            this.getLogger().debug("END act map={}");
        }

        return result;
    }

}