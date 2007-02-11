/*
 * Copyright 1999-2004 The Apache Software Foundation.
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
package org.apache.garbage.tree;

/**
 * 
 * 
 * @author <a href="mailto:pier@apache.org">Pier Fumagalli</a>, February 2003
 * @version CVS $Id: AbstractEvent.java,v 1.2 2004/03/05 10:07:24 bdelacretaz Exp $
 */
public abstract class AbstractEvent implements Event {

    /**
     * If possible, merge this <code>Event</code> to another.
     * <br />
     * By default all <code>AbstractEvent</code> instances will not merge with
     * each other. Solid implementations of this class will have to override
     * this method and (if extending the <code>LocatedEvent</code> abstract
     * class) call {@link LocatedEvent#mergeLocation(LocatedEvent)} to update
     * location information.
     *
     * @param event The <code>Event</code> to which this one should be merged.
     * @return Always <b>false</b>.
     * @see LocatedEvent#mergeLocation(LocatedEvent)
     */
    public boolean merge(Event event) {
        return(false);
    }
}