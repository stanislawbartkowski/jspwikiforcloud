<%@ page import="org.apache.commons.logging.*" %>
<%@ page import="org.apache.wiki.*" %>
<%@ page import="org.apache.wiki.tags.BreadcrumbsTag" %>
<%@ page import="org.apache.wiki.tags.BreadcrumbsTag.FixedQueue" %>
<%@ page import="java.util.*" %>
<%@ page import="org.apache.wiki.spring.BeanHolder" %>
<%@ page import="org.apache.wiki.attachment.Attachment" %>
<%@ page errorPage="/Error.jsp" %>
<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>

<%!  
    Log log = LogFactory.getLog("JSPWiki");
%>

<%
    WikiEngine wiki = WikiEngine.getInstance( getServletConfig() );
    // Create wiki context and check for authorization
    WikiContext wikiContext = wiki.createContext( request, WikiContext.DELETE );
    if(!wikiContext.hasAccess( response )) return;
    String pagereq = wikiContext.getName();

    WikiPage wikipage      = wikiContext.getPage();
    WikiPage latestversion = wiki.getPage( pagereq );

    String delete = request.getParameter( "delete" );
    String deleteall = request.getParameter( "delete-all" );

    if( latestversion == null )
    {
        latestversion = wikiContext.getPage();
    }

    // If deleting an attachment, go to the parent page.
    String redirTo = pagereq;
    if( wikipage instanceof Attachment ) {
        redirTo = ((Attachment)wikipage).getParentName();
    }

    if( deleteall != null )
    {
        log.info("Deleting page "+pagereq+". User="+request.getRemoteUser()+", host="+request.getRemoteAddr() );

        wiki.deletePage( pagereq );

        FixedQueue trail = (FixedQueue) session.getAttribute( BreadcrumbsTag.BREADCRUMBTRAIL_KEY );
        if( trail != null )
        {
            trail.removeItem( pagereq );
            session.setAttribute( BreadcrumbsTag.BREADCRUMBTRAIL_KEY, trail );
        }

        response.sendRedirect(wikiContext.getURL( WikiContext.VIEW, pagereq ) );
        return;
    }
    else if( delete != null )
    {
        log.info("Deleting a range of pages from "+pagereq);
        
        for( Enumeration params = request.getParameterNames(); params.hasMoreElements(); )
        {
            String paramName = (String)params.nextElement();
            
            if( paramName.startsWith("delver") )
            {
                int version = Integer.parseInt( paramName.substring(7) );
                
                WikiPage p = wiki.getPage( pagereq, version );
                
                log.debug("Deleting version "+version);
                wiki.deleteVersion( p );
            }
        }
        
        response.sendRedirect(wiki.getURL( WikiContext.INFO, redirTo, null, false ));
        return; 
    }

    // Set the content type and include the response content
    // FIXME: not so.
    response.setContentType("text/html; charset="+wiki.getContentEncoding() );
    String contentPage = BeanHolder.getTemplateManager().findJSP( pageContext,
                                                            wikiContext.getTemplate(),
                                                            "EditTemplate.jsp" );
%><wiki:Include page="<%=contentPage%>" />

