<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>
<%@ page import="org.apache.wiki.*" %>
<%@ page import="org.apache.wiki.ui.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ page import="com.jsp.util.localize.LocaleSupport" %>
<fmt:setLocale value="${prefs.Language}" />
<fmt:setBundle basename="templates.default"/>
<% 
  WikiContext context = WikiContext.findContext( pageContext ); 
  TemplateManager.addResourceRequest( context, TemplateManager.RESOURCE_SCRIPT, "scripts/jspwiki-prefs.js" );
%>

<wiki:TabbedSection defaultTab="${param.tab}">

  <wiki:Tab id="prefs" title='<%=LocaleSupport.getLocalizedMessage(pageContext, "prefs.tab.prefs")%>' accesskey="p" >
     <wiki:Include page="PreferencesTab.jsp" />
  </wiki:Tab>

  <wiki:UserCheck status="authenticated">
  <wiki:Permission permission="editProfile">
  <wiki:Tab id="profile" title='<%=LocaleSupport.getLocalizedMessage(pageContext, "prefs.tab.profile")%>' accesskey="o" >
     <wiki:Include page="ProfileTab.jsp" />
  </wiki:Tab>
  </wiki:Permission>
  </wiki:UserCheck>
  
  <wiki:Permission permission="createGroups"> <!-- FIXME check right permissions -->
  <wiki:Tab id="group" title='<%=LocaleSupport.getLocalizedMessage(pageContext, "group.tab")%>' accesskey="g" >
    <wiki:Include page="GroupTab.jsp" />
  </wiki:Tab>
  </wiki:Permission>

</wiki:TabbedSection>