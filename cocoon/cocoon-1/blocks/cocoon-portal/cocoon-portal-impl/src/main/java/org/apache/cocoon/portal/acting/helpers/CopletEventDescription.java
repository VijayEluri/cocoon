/*
 * Copyright 1999-2005 The Apache Software Foundation.
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
package org.apache.cocoon.portal.acting.helpers;

import org.apache.cocoon.portal.PortalService;
import org.apache.cocoon.portal.event.Event;

/**
 * Helper class for an coplet event.
 *
 * @version $Id$
 */
public class CopletEventDescription extends CopletMapping {

    public Object data;

    /**
     * @see org.apache.cocoon.portal.acting.helpers.Mapping#getEvent(org.apache.cocoon.portal.PortalService, java.lang.Object)
     */
    public Event getEvent(PortalService service, Object eventData) {
        return super.getEvent(service, (eventData == null ? this.data : eventData));
    }
}