/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.xml;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.core.NamespaceRegistryImpl;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An <code>ImportHandler</code> instance can be used to import serialized
 * data in System View XML or Document View XML. Processing of the XML is
 * handled by specialized <code>ContentHandler</code>s
 * (i.e. <code>SysViewImportHandler</code> and <code>DocViewImportHandler</code>).
 * <p/>
 * The actual task of importing though is delegated to the implementation of
 * the <code>{@link Importer}</code> interface.
 * <p/>
 * <b>Important Note:</b>
 * <p/>
 * These SAX Event Handlers expect that Namespace URI's and local names are
 * reported in the <code>start/endElement</code> events and that
 * <code>start/endPrefixMapping</code> events are reported
 * (i.e. default SAX2 Namespace processing).
 */
public class ImportHandler extends DefaultHandler {

    private static Logger log = LoggerFactory.getLogger(ImportHandler.class);

    protected final Importer importer;
    protected final NamespaceRegistryImpl nsReg;
    protected final NamespaceResolver nsResolver;

    protected Locator locator;

    private TargetImportHandler targetHandler;

    /**
     * The local namespace mappings reported by
     * {@link #startPrefixMapping(String, String)}. These mappings are used
     * to instantiate the local namespace context in
     * {@link #startElement(String, String, String, Attributes)}.
     */
    private Map localNamespaceMappings;

    public ImportHandler(Importer importer, NamespaceResolver nsResolver,
                         NamespaceRegistryImpl nsReg) {
        this.importer = importer;
        this.nsResolver = nsResolver;
        this.nsReg = nsReg;
    }

    //---------------------------------------------------------< ErrorHandler >
    /**
     * {@inheritDoc}
     */
    public void warning(SAXParseException e) throws SAXException {
        // log exception and carry on...
        log.warn("warning encountered at line: " + e.getLineNumber()
                + ", column: " + e.getColumnNumber()
                + " while parsing XML stream", e);
    }

    /**
     * {@inheritDoc}
     */
    public void error(SAXParseException e) throws SAXException {
        // log exception and carry on...
        log.error("error encountered at line: " + e.getLineNumber()
                + ", column: " + e.getColumnNumber()
                + " while parsing XML stream: " + e.toString());
    }

    /**
     * {@inheritDoc}
     */
    public void fatalError(SAXParseException e) throws SAXException {
        // log and re-throw exception
        log.error("fatal error encountered at line: " + e.getLineNumber()
                + ", column: " + e.getColumnNumber()
                + " while parsing XML stream: " + e.toString());
        throw e;
    }

    //-------------------------------------------------------< ContentHandler >
    /**
     * {@inheritDoc}
     */
    public void startDocument() throws SAXException {
        targetHandler = null;

        try {
            localNamespaceMappings = new HashMap();
            String[] uris = nsReg.getURIs();
            for (int i = 0; i < uris.length; i++) {
                localNamespaceMappings.put(
                        nsResolver.getPrefix(uris[i]), uris[i]);
            }
        } catch (RepositoryException re) {
            throw new SAXException(re);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void endDocument() throws SAXException {
        // delegate to target handler
        targetHandler.endDocument();
    }

    /**
     * Records the given namespace mapping to be included in the local
     * namespace context. The local namespace context is instantiated
     * in {@link #startElement(String, String, String, Attributes)} using
     * all the the namespace mappings recorded for the current XML element.
     * <p>
     * The namespace is also recorded in the persistent namespace registry
     * unless it is already known.
     *
     * @param prefix namespace prefix
     * @param uri namespace URI
     */
    public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
        localNamespaceMappings.put(prefix, uri);
        try {
            // Register the namespace unless already registered
            nsReg.safeRegisterNamespace(prefix, uri);
        } catch (RepositoryException re) {
            throw new SAXException(re);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void endPrefixMapping(String prefix) throws SAXException {
        /**
         * nothing to do here as namespace context has already been popped
         * in endElement event
         */
    }

    /**
     * {@inheritDoc}
     */
    public void startElement(String namespaceURI, String localName, String qName,
                             Attributes atts) throws SAXException {
        if (targetHandler == null) {
            // the namespace of the first element determines the type of XML
            // (system view/document view)
            if (Name.NS_SV_URI.equals(namespaceURI)) {
                targetHandler = new SysViewImportHandler(importer);
            } else {
                targetHandler = new DocViewImportHandler(importer);
            }

            targetHandler.startDocument();
        }

        // Start a namespace context for this element
        targetHandler.startNamespaceContext(localNamespaceMappings);
        localNamespaceMappings.clear();

        // delegate to target handler
        targetHandler.startElement(namespaceURI, localName, qName, atts);
    }

    /**
     * {@inheritDoc}
     */
    public void characters(char[] ch, int start, int length) throws SAXException {
        // delegate to target handler
        targetHandler.characters(ch, start, length);
    }

    /**
     * Delegates the call to the underlying target handler and asks the
     * handler to end the current namespace context.
     * {@inheritDoc}
     */
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {
        targetHandler.endElement(namespaceURI, localName, qName);
        targetHandler.endNamespaceContext();
    }

    /**
     * {@inheritDoc}
     */
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }
}
