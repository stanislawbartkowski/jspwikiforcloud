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
package org.apache.wiki;

import java.security.AccessControlException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wiki.auth.AuthenticationManager;
import org.apache.wiki.auth.GroupPrincipal;
import org.apache.wiki.auth.NoSuchPrincipalException;
import org.apache.wiki.auth.UserManager;
import org.apache.wiki.auth.WikiPrincipal;
import org.apache.wiki.auth.authorize.Group;
import org.apache.wiki.auth.authorize.GroupManager;
import org.apache.wiki.auth.authorize.Role;
import org.apache.wiki.auth.user.UserDatabase;
import org.apache.wiki.auth.user.UserProfile;
import org.apache.wiki.event.WikiEvent;
import org.apache.wiki.event.WikiEventListener;
import org.apache.wiki.event.WikiSecurityEvent;
import org.apache.wiki.security.WikiSubject;
import org.apache.wiki.spring.BeanHolder;

/**
 * <p>
 * Represents a long-running wiki session, with an associated user Principal,
 * user Subject, and authentication status. This class is initialized with
 * minimal, default-deny values: authentication is set to <code>false</code>,
 * and the user principal is set to <code>null</code>.
 * </p>
 * <p>
 * The WikiSession class allows callers to:
 * </p>
 * <ul>
 * <li>Obtain the authentication status of the user via {@link #isAnonymous()}
 * and {@link #isAuthenticated()}</li>
 * <li>Query the session for Principals representing the user's identity via
 * {@link #getLoginPrincipal()}, {@link #getUserPrincipal()} and
 * {@link #getPrincipals()}</li>
 * <li>Store, retrieve and clear UI messages via {@link #addMessage(String)},
 * {@link #getMessages(String)} and {@link #clearMessages(String)}</li>
 * </ul>
 * <p>
 * To keep track of the Principals each user posseses, each WikiSession stores a
 * JAAS Subject. Various login processes add or remove Principals when users
 * authenticate or log out.
 * </p>
 * <p>
 * WikiSession implements the {@link org.apache.wiki.event.WikiEventListener}
 * interface and listens for group add/change/delete events fired by event
 * sources the WikiSession is registered with. Normally,
 * {@link org.apache.wiki.auth.AuthenticationManager} registers each WikiSession
 * with the {@link org.apache.wiki.auth.authorize.GroupManager} so it can catch
 * group events. Thus, when a user is added to a
 * {@link org.apache.wiki.auth.authorize.Group}, a corresponding
 * {@link org.apache.wiki.auth.GroupPrincipal} is injected into the Subject's
 * Principal set. Likewise, when the user is removed from the Group or the Group
 * is deleted, the GroupPrincipal is removed from the Subject. The effect that
 * this strategy produces is extremely beneficial: when someone adds a user to a
 * wiki group, that user <em>immediately</em> gains the privileges associated
 * with that group; he or she does not need to re-authenticate.
 * </p>
 * <p>
 * In addition to methods for examining individual <code>WikiSession</code>
 * objects, this class also contains a number of static methods for managing
 * WikiSessions for an entire wiki. These methods allow callers to find, query
 * and remove WikiSession objects, and to obtain a list of the current wiki
 * session users.
 * </p>
 * <p>
 * WikiSession encloses a protected static class, {@link SessionMonitor}, to
 * keep track of WikiSessions registered with each wiki.
 * </p>
 */
public final class WikiSession implements WikiEventListener {

    /** An anonymous user's session status. */
    public static final String ANONYMOUS = "anonymous";

    /** An asserted user's session status. */
    public static final String ASSERTED = "asserted";

    /** An authenticated user's session status. */
    public static final String AUTHENTICATED = "authenticated";

    private static final int ONE = 48;

    private static final int NINE = 57;

    private static final int DOT = 46;

    private static final Log log = LogFactory.getLog(WikiSession.class);

    private static final String ALL = "*";

    private final WikiSubject userSubject = new WikiSubject();

    private final Map<String, Set<String>> m_messages = new HashMap<String, Set<String>>();

    /** The WikiEngine that created this session. */
    // private WikiEngine m_engine = null;

    private String m_status = ANONYMOUS;

    private Principal m_userPrincipal = WikiPrincipal.GUEST;

    private Principal m_loginPrincipal = WikiPrincipal.GUEST;

    private boolean useJAAS;

    public WikiSubject getWikiSubject() {
        return userSubject;
    }

    /**
     * Returns <code>true</code> if one of this WikiSession's user Principals
     * can be shown to belong to a particular wiki group. If the user is not
     * authenticated, this method will always return <code>false</code>.
     * 
     * @param group
     *            the group to test
     * @return the result
     */
    private final boolean isInGroup(Group group) {
        for (Principal principal : getPrincipals()) {
            if (isAuthenticated() && group.isMember(principal)) {
                return true;
            }
        }
        return false;
    }

    public WikiSession() {
        log.trace("New WikiSession");
        makeGuestUser();
        useJAAS = BeanHolder.getIsJAAS();
    }

    /**
     * Returns <code>true</code> if the user is considered asserted via a
     * session cookie; that is, the Subject contains the Principal
     * Role.ASSERTED.
     * 
     * @return Returns <code>true</code> if the user is asserted
     */
    private final boolean isAsserted() {
        return userSubject.getPrincipals().contains(Role.ASSERTED);
    }

    /**
     * Returns the authentication status of the user's session. The user is
     * considered authenticated if the Subject contains the Principal
     * Role.AUTHENTICATED. If this method determines that an earlier LoginModule
     * did not inject Role.AUTHENTICATED, it will inject one if the user is not
     * anonymous <em>and</em> not asserted.
     * 
     * @return Returns <code>true</code> if the user is authenticated
     */
    public final boolean isAuthenticated() {
        if (!useJAAS) {
            return true;
        }
        log.debug("Test : isAuthenticated");
        // If Role.AUTHENTICATED is in principals set, always return true.
        if (userSubject.getPrincipals().contains(Role.AUTHENTICATED)) {
            log.debug("OK. is Authenticated");
            return true;
        }
        log.debug("Not authenticated");

        // With non-JSPWiki LoginModules, the role may not be there, so
        // we need to add it if the user really is authenticated.
        if (!isAnonymous() && !isAsserted()) {
            // Inject AUTHENTICATED role
            log.debug("Not autheticated and not asserted");
            userSubject.getPrincipals().add(Role.AUTHENTICATED);
            return true;
        }
        log.debug("Aautheticated or Asserted");
        return false;
    }

    /**
     * <p>
     * Determines whether the current session is anonymous. This will be true if
     * any of these conditions are true:
     * </p>
     * <ul>
     * <li>The session's Principal set contains
     * {@link org.apache.wiki.auth.authorize.Role#ANONYMOUS}</li>
     * <li>The session's Principal set contains
     * {@link org.apache.wiki.auth.WikiPrincipal#GUEST}</li>
     * <li>The Principal returned by {@link #getUserPrincipal()} evaluates to an
     * IP address.</li>
     * </ul>
     * <p>
     * The criteria above are listed in the order in which they are
     * evalPrincipal*
     * </p>
     * 
     * @return whether the current user's identity is equivalent to an IP
     *         address
     */
    public final boolean isAnonymous() {

        if (!useJAAS) {
            return true;
        }

        Set<Principal> principals = userSubject.getPrincipals();
        return principals.contains(Role.ANONYMOUS)
                || principals.contains(WikiPrincipal.GUEST)
                || isIPV4Address(getUserPrincipal().getName());
    }

    /**
     * <p>
     * Returns the Principal used to log in to an authenticated session. The
     * login principal is determined by examining the Subject's Principal set
     * for PrincipalWrappers or WikiPrincipals with type designator
     * <code>LOGIN_NAME</code>; the first one found is the login principal. If
     * one is not found, this method returns the first principal that isn't of
     * type Role or GroupPrincipal. If neither of these conditions hold, this
     * method returns {@link org.apache.wiki.auth.WikiPrincipal#GUEST}.
     * 
     * @return the login Principal. If it is a PrincipalWrapper containing an
     *         externally-provided Principal, the object returned is the
     *         Principal, not the wrapper around it.
     */
    public final Principal getLoginPrincipal() {
        return m_loginPrincipal;
    }

    /**
     * <p>
     * Returns the primary user Principal associated with this session. The
     * primary user principal is determined as follows:
     * </p>
     * <ol>
     * <li>If the Subject's Principal set contains WikiPrincipals, the first
     * WikiPrincipal with type designator <code>WIKI_NAME</code> or
     * (alternatively) <code>FULL_NAME</code> is the primary Principal.</li>
     * <li>For all other cases, the first Principal in the Subject's principal
     * collection that that isn't of type Role or GroupPrincipal is the primary.
     * </li>
     * </ol>
     * If no primary user Principal is found, this method returns
     * {@link org.apache.wiki.auth.WikiPrincipal#GUEST}.
     * 
     * @return the primary user Principal
     */
    public final Principal getUserPrincipal() {
        return m_userPrincipal;
    }

    /**
     * Returns a cached Locale object for this user. It's better to use
     * WikiContext's corresponding getBundle() method, since that will actually
     * react if the user changes the locale in the middle, but if that's not
     * available (or, for some reason, you need the speed), this method can also
     * be used. The Locale expires when the WikiSession expires, and currently
     * there is no way to reset the Locale.
     * 
     * @return A cached Locale object
     * @since 2.5.96
     */
    public final Locale getLocale() {
        // return m_cachedLocale;
        Locale lo = BeanHolder.getLocale();
        if (lo == null) {
            return Locale.getDefault();
        }
        return lo;
    }

    /**
     * Adds a message to the generic list of messages associated with the
     * session. These messages retain their order of insertion and remain until
     * the {@link #clearMessages()} method is called.
     * 
     * @param message
     *            the message to add; if <code>null</code> it is ignored.
     */
    public final void addMessage(String message) {
        addMessage(ALL, message);
    }

    /**
     * Adds a message to the specific set of messages associated with the
     * session. These messages retain their order of insertion and remain until
     * the {@link #clearMessages()} method is called.
     * 
     * @param topic
     *            the topic to associate the message to;
     * @param message
     *            the message to add
     */
    public final void addMessage(String topic, String message) {
        if (topic == null) {
            throw new IllegalArgumentException(
                    "addMessage: topic cannot be null.");
        }
        if (message == null) {
            message = "";
        }
        Set<String> messages = m_messages.get(topic);
        if (messages == null) {
            messages = new LinkedHashSet<String>();
            m_messages.put(topic, messages);
        }
        messages.add(message);
    }

    /**
     * Clears all messages associated with this session.
     */
    public final void clearMessages() {
        m_messages.clear();
    }

    /**
     * Clears all messages associated with a session topic.
     * 
     * @param topic
     *            the topic whose messages should be cleared.
     */
    public final void clearMessages(String topic) {
        Set<String> messages = m_messages.get(topic);
        if (messages != null) {
            m_messages.clear();
        }
    }

    /**
     * Returns all generic messages associated with this session. The messages
     * stored with the session persist throughout the session unless they have
     * been reset with {@link #clearMessages()}.
     * 
     * @return the current messages.
     */
    public final String[] getMessages() {
        return getMessages(ALL);
    }

    /**
     * Returns all messages associated with a session topic. The messages stored
     * with the session persist throughout the session unless they have been
     * reset with {@link #clearMessages(String)}.
     * 
     * @return the current messages.
     * @param topic
     *            The topic
     */
    public final String[] getMessages(String topic) {
        Set<String> messages = m_messages.get(topic);
        if (messages == null || messages.size() == 0) {
            return new String[0];
        }
        return messages.toArray(new String[messages.size()]);
    }

    /**
     * Returns all user Principals associated with this session. User principals
     * are those in the Subject's principal collection that aren't of type Role
     * or of type GroupPrincipal. This is a defensive copy.
     * 
     * @return Returns the user principal
     * @see org.apache.wiki.auth.AuthenticationManager#isUserPrincipal(Principal)
     */
    public final Principal[] getPrincipals() {
        ArrayList<Principal> principals = new ArrayList<Principal>();

        // Take the first non Role as the main Principal
        for (Principal principal : userSubject.getPrincipals()) {
            if (AuthenticationManager.isUserPrincipal(principal)) {
                principals.add(principal);
            }
        }

        return principals.toArray(new Principal[principals.size()]);
    }

    /**
     * Returns an array of Principal objects that represents the groups and
     * roles that the user associated with a WikiSession possesses. The array is
     * built by iterating through the Subject's Principal set and extracting all
     * Role and GroupPrincipal objects into a list. The list is returned as an
     * array sorted in the natural order implied by each Principal's
     * <code>getName</code> method. Note that this method does <em>not</em>
     * consult the external Authorizer or GroupManager; it relies on the
     * Principals that have been injected into the user's Subject at login time,
     * or after group creation/modification/deletion.
     * 
     * @return an array of Principal objects corresponding to the roles the
     *         Subject possesses
     */
    public final Principal[] getRoles() {
        Set<Principal> roles = new HashSet<Principal>();

        // Add all of the Roles possessed by the Subject directly
        roles.addAll(userSubject.getPrincipals(Role.class));

        // Add all of the GroupPrincipals possessed by the Subject directly
        roles.addAll(userSubject.getPrincipals(GroupPrincipal.class));

        // Return a defensive copy
        Principal[] roleArray = roles.toArray(new Principal[roles.size()]);
        Arrays.sort(roleArray, WikiPrincipal.COMPARATOR);
        return roleArray;
    }

    /**
     * Removes the wiki session associated with the user's HTTP request from the
     * cache of wiki sessions, typically as part of a logout process.
     * 
     * @param engine
     *            the wiki engine
     * @param request
     *            the users's HTTP request
     */
    public static final void removeWikiSession(WikiEngine engine,
            HttpServletRequest request) {
    }

    /**
     * Returns <code>true</code> if the WikiSession's Subject possess a supplied
     * Principal. This method eliminates the need to externally request and
     * inspect the JAAS subject.
     * 
     * @param principal
     *            the Principal to test
     * @return the result
     */
    public final boolean hasPrincipal(Principal principal) {
        return userSubject.getPrincipals().contains(principal);

    }

    /**
     * Listens for WikiEvents generated by source objects such as the
     * GroupManager. This method adds Principals to the private Subject managed
     * by the WikiSession.
     * 
     * @see org.apache.wiki.event.WikiEventListener#actionPerformed(org.apache.wiki.event.WikiEvent)
     */
    public final void actionPerformed(WikiEvent event) {
        if (event instanceof WikiSecurityEvent) {
            WikiSecurityEvent e = (WikiSecurityEvent) event;
            log.debug("Security action performed " + e.eventName(e.getType()));
            if (e.getTarget() != null) {
                switch (e.getType()) {
                case WikiSecurityEvent.GROUP_ADD: {
                    Group group = (Group) e.getTarget();
                    if (isInGroup(group)) {
                        userSubject.getPrincipals().add(group.getPrincipal());
                    }
                    break;
                }
                case WikiSecurityEvent.GROUP_REMOVE: {
                    Group group = (Group) e.getTarget();
                    if (userSubject.getPrincipals().contains(
                            group.getPrincipal())) {
                        userSubject.getPrincipals()
                                .remove(group.getPrincipal());
                    }
                    break;
                }
                case WikiSecurityEvent.GROUP_CLEAR_GROUPS: {
                    userSubject.getPrincipals().removeAll(
                            userSubject.getPrincipals(GroupPrincipal.class));
                    break;
                }
                case WikiSecurityEvent.LOGIN_INITIATED: {
                    // Do nothing
                }
                case WikiSecurityEvent.PRINCIPAL_ADD: {
                    WikiSession target = (WikiSession) e.getTarget();
                    if (this.equals(target) && m_status == AUTHENTICATED) {
                        Set<Principal> principals = userSubject.getPrincipals();
                        principals.add((Principal) e.getPrincipal());
                    }
                    break;
                }
                case WikiSecurityEvent.LOGIN_ANONYMOUS: {
                    WikiSession target = (WikiSession) e.getTarget();
                    if (this.equals(target)) {
                        m_status = ANONYMOUS;

                        // Set the login/user principals and login status
                        Set<Principal> principals = userSubject.getPrincipals();
                        m_loginPrincipal = (Principal) e.getPrincipal();
                        m_userPrincipal = m_loginPrincipal;

                        // Add the login principal to the Subject, and set the
                        // built-in roles
                        principals.clear();
                        principals.add(m_loginPrincipal);
                        principals.add(Role.ALL);
                        principals.add(Role.ANONYMOUS);
                    }
                    break;
                }
                case WikiSecurityEvent.LOGIN_ASSERTED: {
                    WikiSession target = (WikiSession) e.getTarget();
                    if (this.equals(target)) {
                        log.debug("SET ASSERTED");
                        m_status = ASSERTED;

                        // Set the login/user principals and login status
                        Set<Principal> principals = userSubject.getPrincipals();
                        m_loginPrincipal = (Principal) e.getPrincipal();
                        m_userPrincipal = m_loginPrincipal;

                        // Add the login principal to the Subject, and set the
                        // built-in roles
                        principals.clear();
                        principals.add(m_loginPrincipal);
                        principals.add(Role.ALL);
                        principals.add(Role.ASSERTED);
                    }
                    break;
                }
                case WikiSecurityEvent.LOGIN_AUTHENTICATED: {
                    log.debug("LOGIN_AUTHETICATED");
                    WikiSession target = (WikiSession) e.getTarget();
                    if (this.equals(target)) {
                        log.debug("LOGIN_AUTHENTICATED : equals");
                        m_status = AUTHENTICATED;

                        // Set the login/user principals and login status
                        Set<Principal> principals = userSubject.getPrincipals();
                        m_loginPrincipal = (Principal) e.getPrincipal();
                        m_userPrincipal = m_loginPrincipal;

                        // Add the login principal to the Subject, and set the
                        // built-in roles
                        principals.clear();
                        principals.add(m_loginPrincipal);
                        principals.add(Role.ALL);
                        principals.add(Role.AUTHENTICATED);

                        // Add the user and group principals
                        injectUserProfilePrincipals(); // Add principals for the
                                                       // user profile
                        injectGroupPrincipals(); // Inject group principals
                    } else {
                        log.debug("LOGIN_AUTHENTICATED : not equals");
                    }
                    break;
                }
                case WikiSecurityEvent.PROFILE_SAVE: {
                    WikiSession source = (WikiSession) e.getSource();
                    if (this.equals(source)) {
                        injectUserProfilePrincipals(); // Add principals for the
                                                       // user profile
                        injectGroupPrincipals(); // Inject group principals
                    }
                    break;
                }
                case WikiSecurityEvent.PROFILE_NAME_CHANGED: {
                    // Refresh user principals based on new user profile
                    WikiSession source = (WikiSession) e.getSource();
                    if (this.equals(source) && m_status == AUTHENTICATED) {
                        // To prepare for refresh, set the new full name as the
                        // primary principal
                        UserProfile[] profiles = (UserProfile[]) e.getTarget();
                        UserProfile newProfile = profiles[1];
                        if (newProfile.getFullname() == null) {
                            throw new IllegalStateException(
                                    "User profile FullName cannot be null.");
                        }

                        Set<Principal> principals = userSubject.getPrincipals();
                        m_loginPrincipal = new WikiPrincipal(
                                newProfile.getLoginName());

                        // Add the login principal to the Subject, and set the
                        // built-in roles
                        principals.clear();
                        principals.add(m_loginPrincipal);
                        principals.add(Role.ALL);
                        principals.add(Role.AUTHENTICATED);

                        // Add the user and group principals
                        injectUserProfilePrincipals(); // Add principals for the
                                                       // user profile
                        injectGroupPrincipals(); // Inject group principals
                    }
                    break;
                }

                //
                // No action, if the event is not recognized.
                //
                default:
                    break;
                }
            }
        }
    }

    private void makeGuestUser() {
        userSubject.getPrincipals().clear();
        userSubject.getPrincipals().add(WikiPrincipal.GUEST);
        userSubject.getPrincipals().add(Role.ANONYMOUS);
        userSubject.getPrincipals().add(Role.ALL);
        m_userPrincipal = WikiPrincipal.GUEST;
        m_loginPrincipal = WikiPrincipal.GUEST;
    }

    /**
     * Invalidates the WikiSession and resets its Subject's Principals to the
     * equivalent of a "guest session".
     */
    public final void invalidate() {
        if (!useJAAS) {
            return;
        }
        makeGuestUser();
    }

    /**
     * Injects GroupPrincipal objects into the user's Principal set based on the
     * groups the user belongs to. For Groups, the algorithm first calls the
     * {@link GroupManager#getRoles()} to obtain the array of GroupPrincipals
     * the authorizer knows about. Then, the method
     * {@link GroupManager#isUserInRole(WikiSession, Principal)} is called for
     * each Principal. If the user is a member of the group, an equivalent
     * GroupPrincipal is injected into the user's principal set. Existing
     * GroupPrincipals are flushed and replaced. This method should generally be
     * called after a user's {@link org.apache.wiki.auth.user.UserProfile} is
     * saved. If the wiki session is null, or there is no matching user profile,
     * the method returns silently.
     */
    private final void injectGroupPrincipals() {
        // Flush the existing GroupPrincipals
        userSubject.getPrincipals().removeAll(
                userSubject.getPrincipals(GroupPrincipal.class));

        // Get the GroupManager and test for each Group
        GroupManager manager = BeanHolder.getGroupManager();
        for (Principal group : manager.getRoles()) {
            if (manager.isUserInRole(this, group)) {
                userSubject.getPrincipals().add(group);
            }
        }
    }

    /**
     * Adds Principal objects to the Subject that correspond to the logged-in
     * user's profile attributes for the wiki name, full name and login name.
     * These Principals will be WikiPrincipals, and they will replace all other
     * WikiPrincipals in the Subject. <em>Note:
     * this method is never called during anonymous or asserted sessions.</em>
     */
    protected final void injectUserProfilePrincipals() {
        // Search for the user profile
        String searchId = m_loginPrincipal.getName();
        if (searchId == null) {
            // Oh dear, this wasn't an authenticated user after all
            log.info("Refresh principals failed because WikiSession had no user Principal; maybe not logged in?");
            return;
        }

        // Look up the user and go get the new Principals
        UserManager userMgr = BeanHolder.getUserManager();
        UserDatabase database = userMgr.getUserDatabase();
        if (database == null) {
            throw new IllegalStateException("User database cannot be null.");
        }
        try {
            UserProfile profile = database.find(searchId);
            Principal[] principals = database.getPrincipals(profile
                    .getLoginName());
            for (Principal principal : principals) {
                // Add the Principal to the Subject
                userSubject.getPrincipals().add(principal);

                // Set the user principal if needed; we prefer FullName, but the
                // WikiName will also work
                boolean isFullNamePrincipal = (principal instanceof WikiPrincipal && ((WikiPrincipal) principal)
                        .getType() == WikiPrincipal.FULL_NAME);
                if (isFullNamePrincipal) {
                    m_userPrincipal = principal;
                } else if (!(m_userPrincipal instanceof WikiPrincipal)) {
                    m_userPrincipal = principal;
                }
            }
        } catch (NoSuchPrincipalException e) {
            // We will get here if the user has a principal but not a profile
            // For example, it's a container-managed user who hasn't set up a
            // profile yet
            log.warn("User profile '"
                    + searchId
                    + "' not found. This is normal for container-auth users who haven't set up a profile yet.");
        }
    }

    /**
     * <p>
     * Returns the status of the wiki session as a text string. Valid values
     * are:
     * </p>
     * <ul>
     * <li>{@link #AUTHENTICATED}</li>
     * <li>{@link #ASSERTED}</li>
     * <li>{@link #ANONYMOUS}</li>
     * </ul>
     * 
     * @return the user's session status
     */
    public final String getStatus() {
        return m_status;
    }

    /**
     * Static factory method that creates a new "guest" session containing a
     * single user Principal {@link org.apache.wiki.auth.WikiPrincipal#GUEST},
     * plus the role principals {@link Role#ALL} and {@link Role#ANONYMOUS}.
     * This method also adds the session as a listener for GroupManager,
     * AuthenticationManager and UserManager events.
     * 
     * @param engine
     *            the wiki engine
     * @return the guest wiki session
     */
    public static final WikiSession guestSession() {
        WikiSession session = new WikiSession();
        session.invalidate();

        // Add the session as listener for GroupManager, AuthManager,
        // UserManager events
        session.addListeners();

        return session;
    }

    public void addListeners() {

    }

    /**
     * Returns a static guest session, which is available for this thread only.
     * This guest session is used internally whenever there is no
     * HttpServletRequest involved, but the request is done e.g. when embedding
     * JSPWiki code.
     * 
     * @param engine
     *            WikiEngine for this session
     * @return A static WikiSession which is shared by all in this same Thread.
     */
    // FIXME: Should really use WeakReferences to clean away unused sessions.

    /**
     * Wrapper for
     * {@link javax.security.auth.Subject#doAsPrivileged(Subject, java.security.PrivilegedExceptionAction, java.security.AccessControlContext)}
     * that executes an action with the privileges posssessed by a WikiSession's
     * Subject. The action executes with a <code>null</code>
     * AccessControlContext, which has the effect of running it "cleanly"
     * without the AccessControlContexts of the caller.
     * 
     * @param session
     *            the wiki session
     * @param action
     *            the privileged action
     * @return the result of the privileged action; may be <code>null</code>
     * @throws java.security.AccessControlException
     *             if the action is not permitted by the security policy
     */
    public static final Object doPrivileged(WikiSession session,
            PrivilegedAction<?> action) throws AccessControlException {
        return WikiSubject.doAsPrivileged(session.userSubject, action);
    }

    /**
     * Verifies whether a String represents an IPv4 address. The algorithm is
     * extremely efficient and does not allocate any objects.
     * 
     * @param name
     *            the address to test
     * @return the result
     */
    protected static final boolean isIPV4Address(String name) {
        if (name.charAt(0) == DOT || name.charAt(name.length() - 1) == DOT) {
            return false;
        }

        int[] addr = new int[] { 0, 0, 0, 0 };
        int currentOctet = 0;
        for (int i = 0; i < name.length(); i++) {
            int ch = name.charAt(i);
            boolean isDigit = ch >= ONE && ch <= NINE;
            boolean isDot = ch == DOT;
            if (!isDigit && !isDot) {
                return false;
            }
            if (isDigit) {
                addr[currentOctet] = 10 * addr[currentOctet] + (ch - ONE);
                if (addr[currentOctet] > 255) {
                    return false;
                }
            } else if (name.charAt(i - 1) == DOT) {
                return false;
            } else {
                currentOctet++;
            }
        }
        return currentOctet == 3;
    }

}
