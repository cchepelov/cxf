/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.feature.transform;

import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.exc.WstxUnexpectedCharException;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.interceptor.transform.CharsetAwareTransformInInterceptor;
import org.apache.cxf.interceptor.transform.CharsetAwareTransformOutInterceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.io.CachedWriter;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/* Provides Stax-based transformation of incoming message.
 */
public class StaxTransformInterceptorsTest {

    /* this message is UTF-8 and states so in the header. Regular case. */
    private static final String MESSAGE_FILE = "message_utf-8.xml";
    private static final String MESSAGE_FILE_UNANNOUNCED = "message_utf-8_unannounced.xml";

    private static final String MESSAGE_FILE_LATIN1_EXPLICIT = "message_iso-8859-1.xml";
    private static final String MESSAGE_FILE_LATIN1_UNANNOUNCED = "message_iso-8859-1_unannounced.xml";

    private InputStream messageIS_utf8;
    private Message message_utf8;

    private InputStream messageIS_utf8_unannounced;
    private Message message_utf8_unannounced;

    private InputStream messageIS_latin1_explicit;
    private Message message_latin1_explicit;
    private Message message_utf8_in_header;
    private Message message_utf16be_in_header;

    private InputStream messageIS_latin1_unannounced;
    private Message message_latin1_unannounced;

    private InputStream messageIS_latin1_in_header;
    private Message message_latin1_in_header;

    private CharsetAwareTransformInInterceptor inInterceptor;
    private CharsetAwareTransformOutInterceptor outInterceptor;

    @Before
    public void setUp() throws TransformerConfigurationException {
        messageIS_utf8 = ClassLoaderUtils.getResourceAsStream(MESSAGE_FILE, this.getClass());
        if (messageIS_utf8 == null) {
            throw new IllegalArgumentException("Cannot load message from path: " + MESSAGE_FILE);
        }
        message_utf8 = new MessageImpl();

        messageIS_utf8_unannounced = ClassLoaderUtils.getResourceAsStream(MESSAGE_FILE_UNANNOUNCED, this.getClass());
        if (messageIS_utf8_unannounced == null) {
            throw new IllegalArgumentException("Cannot load message from path: " + MESSAGE_FILE_UNANNOUNCED);
        }
        message_utf8_unannounced = new MessageImpl();

        messageIS_latin1_explicit = ClassLoaderUtils.getResourceAsStream(MESSAGE_FILE_LATIN1_EXPLICIT, this.getClass());
        if (messageIS_latin1_explicit == null) {
            throw new IllegalArgumentException("Cannot load message from path: " + MESSAGE_FILE_LATIN1_EXPLICIT);
        }
        message_latin1_explicit = new MessageImpl();

        messageIS_latin1_unannounced = ClassLoaderUtils.getResourceAsStream(MESSAGE_FILE_LATIN1_UNANNOUNCED, this.getClass());
        if (messageIS_latin1_unannounced == null) {
            throw new IllegalArgumentException("Cannot load message from path: " + MESSAGE_FILE_LATIN1_UNANNOUNCED);
        }
        message_latin1_unannounced = new MessageImpl();

        messageIS_latin1_in_header = ClassLoaderUtils.getResourceAsStream(MESSAGE_FILE_LATIN1_UNANNOUNCED, this.getClass());
        if (messageIS_latin1_in_header == null) {
            throw new IllegalArgumentException("Cannot load message from path: " + MESSAGE_FILE_LATIN1_UNANNOUNCED);
        }
        message_latin1_in_header = new MessageImpl();
        message_latin1_in_header.put(Message.ENCODING, StandardCharsets.ISO_8859_1.name());

        message_utf8_in_header = new MessageImpl();
        message_utf8_in_header.put(Message.ENCODING, StandardCharsets.UTF_8.name());

        message_utf16be_in_header = new MessageImpl();
        message_utf16be_in_header.put(Message.ENCODING, StandardCharsets.UTF_16BE.name());

        Map<String, String> staxTransforms = new HashMap<String, String>();
        staxTransforms.put("{http://customerservice.example.com/}getCustomersByName",
                "{http://customerservice.example.com/}getCustomersByName1");

        inInterceptor = new CharsetAwareTransformInInterceptor();
        inInterceptor.setInTransformElements(staxTransforms);

        outInterceptor = new CharsetAwareTransformOutInterceptor();
        outInterceptor.setOutTransformElements(staxTransforms);
    }

    private void inStreamTest(Message message, InputStream messageIS) throws Exception {
        message.setContent(InputStream.class, messageIS);
        inInterceptor.handleMessage(message);
        XMLStreamReader transformedXReader = message.getContent(XMLStreamReader.class);
        Document doc = StaxUtils.read(transformedXReader);
        Assert.assertTrue("Message was not transformed", checkTransformedXML(doc));
    }

    @Test
    public void inStreamTest_utf8_regular() throws Exception {
        /* regular case */
        inStreamTest(message_utf8, messageIS_utf8);
    }

    @Test
    public void inStreamTest_utf8_unannounced() throws Exception {
        /* correct case as UTF-8 usage is implied */
        inStreamTest(message_utf8_unannounced, messageIS_utf8_unannounced);
    }

    @Test(expected = WstxIOException.class)
    public void inStreamTest_latin1_explicit() throws Exception {
        /* the header encoding (or its lack, interpreted as UTF-8) trumps the encoding declaration within the XML payload */
        inStreamTest(message_latin1_explicit, messageIS_latin1_explicit);
    }

    @Test(expected = WstxIOException.class)
    public void inStreamTest_latin1_payload_bogus_utf8_header() throws Exception {
        /* the header encoding trumps the encoding declaration within the XML payload */
        inStreamTest(message_utf8_in_header, messageIS_latin1_explicit);
    }

    @Test(expected = WstxUnexpectedCharException.class)
    public void inStreamTest_latin1_payload_bogus_utf16be_header() throws Exception {
        /* the header encoding trumps the encoding declaration within the XML payload */
        inStreamTest(message_utf16be_in_header, messageIS_latin1_explicit);
    }

    @Test(expected = WstxUnexpectedCharException.class)
    public void inStreamTest_utf8_payload_bogus_utf16be_header() throws Exception {
        /* the header encoding trumps the encoding declaration within the XML payload */
        inStreamTest(message_utf16be_in_header, messageIS_utf8);
    }

    @Test(expected = WstxIOException.class) /* we expect an unannounced Latin1 document to fail */
    public void inStreamTest_latin1_implicit() throws Exception {
        /* failure expected as an XML payload which isn't UTF-8 and doesn't announce its encoding and where
        the message header itself lacks encoding info cannot be safely decoded */
        inStreamTest(message_latin1_unannounced, messageIS_latin1_unannounced);
    }

    @Test
    public void inStreamTest_latin1_in_header() throws Exception {
        /* correct case as even though the XML document lacks encoding information, the Message metadata provides it */
        inStreamTest(message_latin1_in_header, messageIS_latin1_in_header);
    }


    private void inXMLStreamTest(Message message, String messageEncoding, InputStream messageIS) throws XMLStreamException {
        XMLStreamReader xReader = StaxUtils.createXMLStreamReader(messageIS, messageEncoding);
        message.setContent(XMLStreamReader.class, xReader);
        message.setContent(InputStream.class, messageIS);
        inInterceptor.handleMessage(message);
        XMLStreamReader transformedXReader = message.getContent(XMLStreamReader.class);
        Document doc = StaxUtils.read(transformedXReader);
        Assert.assertTrue("Message was not transformed", checkTransformedXML(doc));
    }


    @Test
    public void inXMLStreamTest() throws XMLStreamException {
        inXMLStreamTest(message_utf8, StandardCharsets.UTF_8.name(), messageIS_utf8);
    }

    @Test
    public void inXMLStreamTest_utf8_unannounced() throws XMLStreamException {
        inXMLStreamTest(message_utf8_unannounced, StandardCharsets.UTF_8.name(), messageIS_utf8_unannounced);
    }

    @Test
    public void inXMLStreamTest_latin1_in_header() throws XMLStreamException {
        inXMLStreamTest(message_latin1_in_header, StandardCharsets.ISO_8859_1.name(), messageIS_latin1_in_header);
    }

    @Test
    public void inXMLStreamTest_latin1_explicit() throws XMLStreamException {
        inXMLStreamTest(message_latin1_explicit, StandardCharsets.ISO_8859_1.name(), messageIS_latin1_explicit);
    }

    private void outStreamTest(Message message, String encoding, InputStream messageIS) throws Exception {
        CachedOutputStream cos = new CachedOutputStream();
        cos.holdTempFile();
        message.setContent(OutputStream.class, cos);
        outInterceptor.handleMessage(message);

        XMLStreamWriter tXWriter = message.getContent(XMLStreamWriter.class);
        StaxUtils.copy(new StreamSource(messageIS), tXWriter);
        tXWriter.close();
        cos.releaseTempFileHold();
        Document doc = StaxUtils.read(cos.getInputStream(), encoding);
        Assert.assertTrue("Message was not transformed", checkTransformedXML(doc));
    }

    @Test
    public void outStreamTest() throws Exception {
        outStreamTest(message_utf8, StandardCharsets.UTF_8.name(), messageIS_utf8);
    }

    @Test
    public void outStreamTest_latin1_explicit() throws Exception {
        /* as soon as the payload says encoding=latin1, this is fine */
        outStreamTest(message_latin1_explicit, StandardCharsets.ISO_8859_1.name(), messageIS_latin1_explicit);
    }

    @Test
    public void outStreamTest_latin1_in_header_explicit() throws Exception {
        /* Note that we DO NOT test/send XML without specifying the encoding within the header */
        outStreamTest(message_latin1_in_header, StandardCharsets.ISO_8859_1.name(), messageIS_latin1_explicit);
    }

    @Test(expected = WstxIOException.class)
    public void outStreamTest_latin1_in_header_unannounced() throws Exception {
        /* Note that we DO NOT test/send XML without specifying the encoding within the header */
        outStreamTest(message_latin1_in_header, StandardCharsets.ISO_8859_1.name(), messageIS_latin1_in_header);
    }


    @Test
    public void outXMLStreamTest() throws XMLStreamException, SAXException, IOException, ParserConfigurationException {
        CachedWriter cWriter = new CachedWriter();
        cWriter.holdTempFile();
        XMLStreamWriter xWriter = StaxUtils.createXMLStreamWriter(cWriter);
        message_utf8.setContent(XMLStreamWriter.class, xWriter);
        outInterceptor.handleMessage(message_utf8);
        XMLStreamWriter tXWriter = message_utf8.getContent(XMLStreamWriter.class);
        StaxUtils.copy(new StreamSource(messageIS_utf8), tXWriter);
        tXWriter.close();
        cWriter.releaseTempFileHold();
        Document doc = StaxUtils.read(cWriter.getReader());
        Assert.assertTrue("Message was not transformed", checkTransformedXML(doc));
    }

    private boolean checkTransformedXML(Document doc) {
        NodeList list = doc.getDocumentElement()
                .getElementsByTagNameNS("http://customerservice.example.com/", "getCustomersByName1");
        return list.getLength() == 1;
    }
}
