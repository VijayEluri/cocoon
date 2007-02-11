/*
* Copyright 1999-2004 The Apache Software Foundation
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package javax.mail.internet;

import javax.mail.Address;

/**
 * Mock class providing the declarations required to compile the Cocoon code when
 * the actual library is not present.
 * 
 * @version CVS $Id: InternetAddress.java,v 1.4 2004/03/06 02:25:44 antonio Exp $
 */
public class InternetAddress extends Address {

    public InternetAddress(String from) {
        throw new NoSuchMethodError("This is a mock object");
    }

    public String getAddress() {
        throw new NoSuchMethodError("This is a mock object");
    }

    public String getPersonal() {
        throw new NoSuchMethodError("This is a mock object");
    }

    public static InternetAddress[] parse(java.lang.String addresslist)
        throws AddressException {
        return null;
    }
}