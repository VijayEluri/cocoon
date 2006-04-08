/*
 * Copyright 2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cocoon.portal.coplet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.cocoon.portal.PortalService;
import org.apache.cocoon.portal.event.CopletInstanceEvent;
import org.apache.cocoon.portal.event.coplet.CopletInstanceSizingEvent;
import org.apache.cocoon.portal.event.coplet.CopletJXPathEvent;
import org.apache.cocoon.portal.layout.CompositeLayout;
import org.apache.cocoon.portal.layout.Item;
import org.apache.cocoon.portal.layout.Layout;
import org.apache.cocoon.portal.layout.impl.CopletLayout;

/**
 * This class contains constants and utility methods for the standard features
 * of a coplet instance.
 *
 * @version $Id$
 */
public final class CopletInstanceDataFeatures {

    protected static final String CHANGED_COPLETS_ATTRIBUTE_NAME = CopletInstanceDataFeatures.class.getName() + "/ChangedCoplets";

    /**
     * Tests if this is a sizing event for a coplet instance.
     */
    public static boolean isSizingEvent(CopletInstanceEvent cie) {
        if ( cie instanceof CopletInstanceSizingEvent ) {
            return true;
        }
        if ( cie instanceof CopletJXPathEvent ) {
            if ( "size".equals(((CopletJXPathEvent)cie).getPath()) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the new size of the sizing event.
     */
    public static int getSize(CopletInstanceEvent cie) {
        if ( cie instanceof CopletInstanceSizingEvent ) {
            return ((CopletInstanceSizingEvent)cie).getSize();
        }
        if ( cie instanceof CopletJXPathEvent ) {
            CopletJXPathEvent e = (CopletJXPathEvent)cie;
            if ( "size".equals(e.getPath()) && e.getValue() != null ) {
                return Integer.valueOf(e.getValue().toString()).intValue();
            }
        }
        return -1;
    }

    /**
     * Search for a layout containing the coplet instance data.
     */
    public static CopletLayout searchLayout(String copletId, Layout rootLayout) {
        if ( rootLayout instanceof CopletLayout ) {
            final CopletLayout cl = (CopletLayout)rootLayout;
            if ( cl.getCopletInstanceData() != null
                 && cl.getCopletInstanceData().getId().equals(copletId) ) {
                return (CopletLayout) rootLayout;
            }
        } else if ( rootLayout instanceof CompositeLayout ) {
            final CompositeLayout cl = (CompositeLayout)rootLayout;
            final Iterator i = cl.getItems().iterator();
            while ( i.hasNext() ) {
                final Item current = (Item)i.next();
                CopletLayout result = searchLayout(copletId, current.getLayout());
                if ( result != null ) {
                    return result;
                }
            }
        }
        return null;
    }

    public static List getChangedCopletInstanceDataObjects(PortalService service) {
        List list = (List)service.getTemporaryAttribute(CHANGED_COPLETS_ATTRIBUTE_NAME);
        if ( list == null ) {
            return Collections.EMPTY_LIST;
        }
        return list;
    }

    public static void addChangedCopletInstanceData(PortalService service,
                                                    CopletInstanceData cid) {
        List list = (List)service.getTemporaryAttribute(CHANGED_COPLETS_ATTRIBUTE_NAME);
        if ( list == null ) {
            list = new ArrayList();
        }
        if ( !list.contains(cid) ) {
            list.add(cid);
        }
        service.setTemporaryAttribute(CHANGED_COPLETS_ATTRIBUTE_NAME, list);
    }

    public static String sizeToString(int value) {
        switch (value) {
            case CopletInstanceData.SIZE_NORMAL : return "normal";
            case CopletInstanceData.SIZE_FULLSCREEN : return "fullscreen";
            case CopletInstanceData.SIZE_MAXIMIZED : return "maximized";
            case CopletInstanceData.SIZE_MINIMIZED : return "minimized";
            default:
                return "";
        }
    }
}
