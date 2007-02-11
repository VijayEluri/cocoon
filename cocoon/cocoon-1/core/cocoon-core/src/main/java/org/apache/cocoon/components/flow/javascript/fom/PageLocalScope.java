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
package org.apache.cocoon.components.flow.javascript.fom;

/**
 * @version $Id$
 */
public interface PageLocalScope {

    boolean has(PageLocal local, String name);

    boolean has(PageLocal local, int index);

    Object get(PageLocal local, String name);

    Object get(PageLocal local, int index);

    void put(PageLocal local, String name, Object value);

    void put(PageLocal local, int index, Object value);

    void delete(PageLocal local, String name);

    void delete(PageLocal local, int index);

    Object[] getIds(PageLocal local);

    Object getDefaultValue(PageLocal local, Class hint);

    PageLocal createPageLocal();
}
