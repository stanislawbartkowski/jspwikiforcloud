/*
    JSPWiki - a JSP-based WikiWiki clone.

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.    
 */
package org.apache.wiki.auth.login;

import java.security.Principal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

//import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log; import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.LogFactory;
import org.apache.wiki.auth.WikiPrincipal;
import org.apache.wiki.security.WikiLoginModule;
import org.apache.wiki.security.WikiSubject;

/**
 * Abstract JAAS {@link javax.security.auth.spi.LoginModule}that implements
 * base functionality. The methods {@link #login()} and {@link #commit()} must
 * be implemented by subclasses. The default implementations of
 * {@link #initialize(Subject, CallbackHandler, Map, Map)}, {@link #abort()} and
 * {@link #logout()} should be sufficient for most purposes.
 * @since 2.3
 */
public abstract class AbstractLoginModule implements WikiLoginModule
{

    private static final Log   log = LogFactory.getLog( AbstractLoginModule.class );

    protected CallbackHandler m_handler;

    protected Map<String,?>             m_options;

    /**
     * Collection of Principals set during login module initialization.
     * These represent the user's identities prior to the overall login.
     * Typically these will contain earlier, less-authoritative principals
     * like a WikiPrincipal for the user cookie, or an IP address.
     * These Principals are forcibly removed during the commit phase
     * if login succeeds.
     * @deprecated
     */
    protected Collection<Principal>      m_previousWikiPrincipals;

    /**
     * Implementing classes should add Principals to this collection; these
     * will be added to the principal set when the overall login succeeds.
     * These Principals will be added to the Subject
     * during the {@link #commit()} phase of login.
     */
    protected Collection<Principal> m_principals;

    /**
     * Implementing classes should add Principals to this collection
     * to specify what Principals <em>must</em> be removed if login for
     * this module, or for the entire login configuration overall, fails.
     * Generally, these will be Principals of type
     * {@link org.apache.wiki.auth.authorize.Role}.
     * @deprecated
     */
    protected Collection<Principal>      m_principalsToRemove;

    /**
     * Implementing classes should add Principals to this collection to specify
     * what Principals, perhaps suppled by other LoginModules, <em>must</em>
     * be removed if login for this module, or for the entire login
     * configuration overall, succeeds. Generally, these will be Principals of
     * type {@link org.apache.wiki.auth.authorize.Role}. For example,
     * {@link CookieAssertionLoginModule} adds
     * {@link org.apache.wiki.auth.authorize.Role#ANONYMOUS} to its
     * <code>m_principalsToOverwrite</code> collection because when it
     * succeeds, its own {@link org.apache.wiki.auth.authorize.Role#AUTHENTICATED}
     * should over-write {@link org.apache.wiki.auth.authorize.Role#ANONYMOUS}.
     * @deprecated
     */
    protected Collection<Principal>      m_principalsToOverwrite;

    protected Map<String,?>             m_state;

    protected WikiSubject         m_subject;

    protected static final String NULL           = "(null)";

    /**
     * Aborts the login; called if the LoginContext's overall authentication
     * failed. (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL
     * LoginModules did not succeed). Specifically, it removes
     * Principals from the Subject that are associated with the
     * individual LoginModule; these will be those contained in
     * {@link #m_principalsToRemove}.
     * It always returns <code>true</code>.
     * @see javax.security.auth.spi.LoginModule#abort()
     * @throws LoginException if the abort itself fails
     * @return True, always.
     */
    public final boolean abort() throws LoginException
    {
        removePrincipals( m_principals );
        removePrincipals( m_principalsToRemove );

        // Clear the principals/principalsToRemove sets
        m_principals.clear();
        m_principalsToRemove.clear();

        return true;
    }

    /**
     * Commits the login. If the overall login method succeeded, adds
     * principals to the Subject's set; generally, these will be the user's
     * actual Principal, plus one or more Role principals. The state of the
     * <code>m_principals</code> member variable is consulted to determine
     * whether to add the principals. If its size is 0 (because the login
     * failed), the login is considered to have failed; in this case,
     * all principals in {@link #m_principalsToRemove} are removed
     * from the Subject's set. Otherwise, the principals added to
     * <code>m_principals</code> in the {@link #login()} method are added to
     * the Subject's set.
     * @return <code>true</code> if the commit succeeded, or
     *         <code>false</code> if the previous call to {@link #login()}
     *         failed
     * @see javax.security.auth.spi.LoginModule#commit()
     */
    public final boolean commit()
    {
        if ( succeeded() )
        {
            removePrincipals( m_previousWikiPrincipals );
            for ( Principal principal : m_principals )
            {
                m_subject.getPrincipals().add( principal );
                if ( log.isDebugEnabled() )
                {
                    log.debug("Committed Principal " + principal.getName() );
                }
            }
            removePrincipals( m_principalsToOverwrite );
            return true;
        }

        // If login did not succeed, clean up after ourselves
        removePrincipals( m_principals );
        removePrincipals( m_principalsToRemove );

        // Clear the principals/principalsToRemove sets
        m_principals.clear();
        m_principalsToRemove.clear();

        return false;
    }

    /**
     * Initializes the LoginModule with a given <code>Subject</code>,
     * callback handler, options and shared state. In particular, the member
     * variable <code>m_principals</code> is initialized as a blank Set.
     * @see javax.security.auth.spi.LoginModule#initialize(javax.security.auth.Subject,
     *      javax.security.auth.callback.CallbackHandler, java.util.Map,
     *      java.util.Map)
     *      
     * @param subject {@inheritDoc}
     * @param callbackHandler {@inheritDoc}
     * @param sharedState {@inheritDoc}
     * @param options {@inheritDoc}
     */
    @Override
    public final void initialize( WikiSubject subject, CallbackHandler callbackHandler, Map<String,?> sharedState, Map<String,?> options )
    {
        m_previousWikiPrincipals = new HashSet<Principal>();
        m_principals = new HashSet<Principal>();
        m_principalsToRemove = new HashSet<Principal>();
        m_principalsToOverwrite = new HashSet<Principal>();
        m_subject = subject;
        m_handler = callbackHandler;
        m_state = sharedState;
        m_options = options;
        if ( subject == null )
        {
            throw new IllegalStateException( "Subject cannot be null" );
        }
        if ( callbackHandler == null )
        {
            throw new IllegalStateException( "Callback handler cannot be null" );
        }
        // Stash the previous WikiPrincipals; we will flush these if login succeeds
        m_previousWikiPrincipals.addAll( subject.getPrincipals( WikiPrincipal.class ) );
    }

    /**
     * Logs in the user by calling back to the registered CallbackHandler with a
     * series of callbacks. If the login succeeds, this method returns
     * <code>true</code>
     * @return <code>true</code> if the commit succeeded, or
     *         <code>false</code> if this LoginModule should be ignored.
     * @throws LoginException if the authentication fails
     * @see javax.security.auth.spi.LoginModule#login()
     */
    public abstract boolean login() throws LoginException;

    /**
     * Logs the user out. Removes all principals in {@link #m_principalsToRemove}
     * from the Subject's principal set.
     * @return <code>true</code> if the commit succeeded, or
     *         <code>false</code> if this LoginModule should be ignored
     * @throws LoginException if the logout itself fails
     * @see javax.security.auth.spi.LoginModule#logout()
     */
    public final boolean logout() throws LoginException
    {
        removePrincipals( m_principals );
        removePrincipals( m_principalsToRemove );

        // Clear the principals/principalsToRemove sets
        m_principals.clear();
        m_principalsToRemove.clear();

        return true;
    }

    /**
     * Returns <code>true</code> if the number of principals
     * contained in {@link #m_principals} is non-zero;
     * <code>false</code> otherwise.
     * @return True, if a login has succeeded.
     */
    private final boolean succeeded()
    {
        return m_principals.size() > 0;
    }

    /**
     * Removes a specified collection of Principals from the Subject's
     * Principal set.
     * @param principals the principals to remove
     */
    private final void removePrincipals( Collection<Principal> principals )
    {
        for ( Principal principal : principals )
        {
            if ( m_subject.getPrincipals().contains( principal ) )
            {
                m_subject.getPrincipals().remove( principal );
                if ( log.isDebugEnabled() )
                {
                    log.debug("Removed Principal " + principal.getName() );
                }
            }
        }
    }

}
