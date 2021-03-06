<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>
<%@ page import="org.apache.wiki.ui.EditorManager" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ page import="com.jsp.util.localize.LocaleSupport" %>
<fmt:setLocale value="${prefs.Language}" />
<fmt:setBundle basename="templates.default"/>

<%-- Inserts page content for preview. --%>
<wiki:TabbedSection>
<wiki:Tab id="previewcontent" title='<%=LocaleSupport.getLocalizedMessage(pageContext, "preview.tab")%>'>

  <div class="information">
    <fmt:message key="preview.info"/>
    <wiki:Editor/>
  </div>

  <div class="previewcontent">
    <wiki:Translate><%=EditorManager.getEditedText(pageContext)%></wiki:Translate>
  </div>

  <div class="information">
    <fmt:message key="preview.info"/>
  </div>

</wiki:Tab>
</wiki:TabbedSection>