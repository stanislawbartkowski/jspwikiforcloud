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
package org.apache.wiki.render;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log; import org.apache.commons.logging.LogFactory;
import org.jdom.JDOMException;
import org.jdom.Text;
import org.jdom.xpath.XPath;

import org.apache.wiki.WikiContext;
import org.apache.wiki.parser.WikiDocument;

/**
 *  A simple renderer that just renders all the text() nodes
 *  from the DOM tree.  This is very useful for cleaning away
 *  all of the XHTML.
 *  
 *  @since  2.4
 */
public class CleanTextRenderer
    extends WikiRenderer
{
    private static final String ALL_TEXT_NODES = "//text()";
    
    protected static final Log log = LogFactory.getLog( CleanTextRenderer.class );
    
    /**
     *  Create a renderer.
     *  
     *  @param context {@inheritDoc}
     *  @param doc {@inheritDoc}
     */
    public CleanTextRenderer( WikiContext context, WikiDocument doc )
    {
        super( context, doc );
    }
    
    /**
     *  {@inheritDoc}
     */
    public String getString()
        throws IOException
    {
        StringBuffer sb = new StringBuffer();
        
        try
        {
            XPath xp = XPath.newInstance( ALL_TEXT_NODES );
        
            List nodes = xp.selectNodes(m_document.getDocument());
            
            for( Iterator i = nodes.iterator(); i.hasNext(); )
            {
                Object el = i.next();
                
                if( el instanceof Text )
                {
                    sb.append( ((Text)el).getValue() );
                }
            }
        }
        catch( JDOMException e )
        {
            log.error("Could not parse XPATH expression");
            throw new IOException( e.getMessage() );
        }
    
        return sb.toString();
    }
}
