<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>
<%@ page import="org.apache.wiki.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ page import="com.jsp.util.localize.LocaleSupport" %><%--CHECK why is this needed --%>
<fmt:setLocale value="${prefs.Language}" />
<fmt:setBundle basename="templates.default"/>
<%
	WikiContext c = WikiContext.findContext( pageContext );
   	WikiPage p = c.getPage();
	String pagename = p.getName();

	/* check possible permalink (blogentry) pages */
	String blogcommentpage="";
	String mainblogpage="";
	if( pagename.indexOf("_blogentry_") != -1 )
	{
		blogcommentpage = TextUtil.replaceString( pagename, "blogentry", "comments" );
		mainblogpage = pagename.substring(0, pagename.indexOf("_blogentry_"));
	}
%>

<%-- If the page is an older version, then offer a note and a possibility
     to restore this version as the latest one. --%>
<wiki:CheckVersion mode="notlatest">
  <form action="<wiki:Link format='url' jsp='Wiki.jsp'/>" 
        method="get"  accept-charset='UTF-8'>

    <input type="hidden" name="page" value="<wiki:Variable var='pagename' />" />     
    <div class="warning">
      <fmt:message key="view.oldversion">
        <fmt:param>
          <%--<wiki:PageVersion/>--%>
          <select id="version" name="version" onchange="this.form.submit();" >
<% 
   int latestVersion = c.getEngine().getPage( pagename, WikiProvider.LATEST_VERSION ).getVersion();
   int thisVersion = p.getVersion();

   if( thisVersion == WikiProvider.LATEST_VERSION ) thisVersion = latestVersion; //should not happen
     for( int i = 1; i <= latestVersion; i++) 
     {
%> 
          <option value="<%= i %>" <%= ((i==thisVersion) ? "selected='selected'" : "") %> ><%= i %></option>
<%
     }    
%>
          </select>
        </fmt:param>
      </fmt:message>  
      <br />
      <wiki:LinkTo><fmt:message key="view.backtocurrent"/></wiki:LinkTo>&nbsp;&nbsp;
      <wiki:EditLink version="this"><fmt:message key="view.restore"/></wiki:EditLink>
    </div>

  </form>
</wiki:CheckVersion>

<%-- Inserts no text if there is no page. --%>
<wiki:InsertPage />

<%-- Inserts blogcomment if appropriate 
<% if( !blogpage.equals("") ) { %>
--%>

<% if( ! mainblogpage.equals("") ) { %>
<wiki:PageExists page="<%= mainblogpage%>">

  <% if( ! blogcommentpage.equals("") ) { %>
  <wiki:PageExists page="<%= blogcommentpage%>">
	<div class="weblogcommentstitle"><fmt:message key="blog.commenttitle"/></div>
    <div class="weblogcomments"><wiki:InsertPage page="<%= blogcommentpage%>" /></div>
  </wiki:PageExists>
  <% }; %>
  <div class="information">	
	<wiki:Link page="<%= mainblogpage %>"><fmt:message key="blog.backtomain"/></wiki:Link>&nbsp; &nbsp;
	<wiki:Link context="comment" page="<%= blogcommentpage%>" ><fmt:message key="blog.addcomments"/></wiki:Link>
  </div>

</wiki:PageExists>
<% }; %>

<wiki:NoSuchPage>
  <%-- FIXME: Should also note when a wrong version has been fetched. --%>
  <div class="information" >
  <fmt:message key="common.nopage">
    <fmt:param><wiki:EditLink><fmt:message key="common.createit"/></wiki:EditLink></fmt:param>
  </fmt:message>
  </div>
</wiki:NoSuchPage>