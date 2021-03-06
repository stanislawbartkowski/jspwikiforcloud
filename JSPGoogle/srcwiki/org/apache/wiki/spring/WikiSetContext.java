package org.apache.wiki.spring;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class WikiSetContext {

	private WikiSetContext() {
	}

	private static final Log log = LogFactory.getLog(WikiSetContext.class);

	public static void setContext(HttpServletRequest request, String logInfo) {
		WikiSetContext.setContext(request.getSession().getServletContext(),
				logInfo);
		request.getSession().setAttribute("aaaa", "bbbbb");
		ApplicationContextHolder.setLocale(request.getLocale());
		log.debug("setContext and attribute for HttpSession");
	}

	public static void setContext(ServletContext context, String logInfo) {
		WebApplicationContext ctx = WebApplicationContextUtils
				.getWebApplicationContext(context);
		ApplicationContextHolder.setContext(ctx);
		log.info(logInfo + " set session context " + context.getServerInfo());
	}

}
