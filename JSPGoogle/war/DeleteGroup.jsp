<%@ page import="org.apache.commons.logging.*" %>
<%@ page import="org.apache.wiki.*" %>
<%@ page import="org.apache.wiki.auth.NoSuchPrincipalException" %>
<%@ page import="org.apache.wiki.auth.WikiSecurityException" %>
<%@ page import="org.apache.wiki.auth.authorize.GroupManager" %>
<%@ page import="org.apache.wiki.spring.BeanHolder" %>
<%@ page errorPage="/Error.jsp" %>
<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>

<%! 
    Log log = LogFactory.getLog("JSPWiki");
%>

<%
    WikiEngine wiki = WikiEngine.getInstance( getServletConfig() );
    // Create wiki context and check for authorization
    WikiContext wikiContext = wiki.createContext( request, WikiContext.DELETE_GROUP );
    if(!wikiContext.hasAccess( response )) return;

    WikiSession wikiSession = wikiContext.getWikiSession();
//    GroupManager groupMgr = wiki.getGroupManager();
    GroupManager groupMgr = BeanHolder.getGroupManager();
    String name = request.getParameter( "group" );
    
    if ( name == null )
    {
        // Group parameter was null
        wikiSession.addMessage( GroupManager.MESSAGES_KEY, "Parameter 'group' cannot be null." );
        response.sendRedirect( "Group.jsp" );
    }

    // Check that the group exists first
    try
    {
        groupMgr.getGroup( name );
    }
    catch ( NoSuchPrincipalException e )
    {
        // Group does not exist
        wikiSession.addMessage( GroupManager.MESSAGES_KEY, e.getMessage() );
        response.sendRedirect( "Group.jsp" );
    }

    // Now, let's delete the group
    try 
    {
        groupMgr.removeGroup( name );
        response.sendRedirect( "." );
    }
    catch ( WikiSecurityException e )
    {
        // Send error message
        wikiSession.addMessage( GroupManager.MESSAGES_KEY, e.getMessage() );
        response.sendRedirect( "Group.jsp" );
    }

%>

