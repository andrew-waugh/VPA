/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPA;

/**
 * Parse
 *
 * This class parses an XML file using SAX. It provides a wrapper around SAX to
 * provide a simple interface designed to efficiently parse XML documents that
 * are typical of data messages where an element either contains a set of
 * subelements, or textual content, but not both. It deals with element content
 * that is Base64 encoded.
 *
 * The parser will NOT validate the XML file against a DTD (i.e. it ignores the
 * DOCTYPE declaration).
 *
 * The parser extends a SAX handler. Handlers are called whenever start element,
 * end element, characters, or comments are encountered.
 *
 * When a start element is encountered, the class builds a representation of the
 * path of elements from the root of the document. It then passes this path and
 * any attributes back to the caller for handling. The caller then instructs the
 * parser what to do with the element. Three options are available: 1. Capture
 * the element and its contents into a String (note that this is not recursive;
 * if an element is being captured, you cannot also capture a subelement. But
 * you can capture the value of a subelement. 2. Capture the value of a leaf
 * element into a String. Note that you cannot capture the value of a non-leaf
 * element. 3. Capture the value of a leaf element into a File. Note that you
 * cannot capture the value of a non-leaf element
 *
 * When an end element is encountered, the class passes the path of the
 * finalised element back to the caller, together with the value of the element.
 *
 * 20180314 Any DOCTYPE in the XML file will now be ignored (i.e. can't validate
 * against a DTD) 20180101 Based on TRIMExport
 */
import VERSCommon.B64;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.Stack;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;

public class XMLParser extends DefaultHandler2 {

    private final SAXParser sax;          // XML parser to perform parse
    private final B64 b64;                // B64 converter for any values that are Base64 encoded

    // global variables storing information about the environment of this parse
    private final XMLConsumer consumer;     // class that consumes start and end of elements

    // support elements
    private Stack<Element> elementsFound;   // stack of the elementName recognised in the parse
    private boolean recordElement;          // true an element is being captured
    // this flag will remain true until the matching end element is encountered
    private String elementBeingCopied;      // path name of element being copied
    private StringBuilder elementCopy;      // the copy of an element
    private StringBuilder elementValue;     // the copy of the value of a leaf element
    private HandleElement he;               // what to do with value in the current element
    private FileOutputStream encfos;        // file to write value to
    private BufferedOutputStream encbos;    // buffered writer
    private OutputStreamWriter encosw;      // to write out non Base64 encoded content

    /**
     * Set up a parser to deal with documents. This parser can be used to
     * repeatedly parse individual documents.
     *
     * @param consumer callback to consumer start and end elements
     * @throws AppFatal if a fatal error occurred
     */
    public XMLParser(XMLConsumer consumer) throws AppFatal {
        super();

        SAXParserFactory spf;

        // set up SAX parser
        try {
            spf = SAXParserFactory.newInstance();
            spf.setValidating(false);
            sax = spf.newSAXParser();
            XMLReader xmlReader = sax.getXMLReader();

            xmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", this);
        } catch (SAXNotRecognizedException | SAXNotSupportedException | ParserConfigurationException e) {
            throw new AppFatal("Failure initiating SAX Parser: " + e.toString());
        } catch (SAXException e) {
            throw new AppFatal("Failure initiating SAX Parser: " + e.toString());
        }

        // set up default global variables
        this.consumer = consumer;
        b64 = new B64();
        recordElement = false;
        elementBeingCopied = null;
        elementCopy = null;
    }

    /**
     * XMLParser the XML
     *
     * This method opens an XML file and calls SAX to parse it looking for
     * information
     *
     * @param xmlFile the file to parse
     * @throws AppError in the event of a VEO error
     * @throws AppFatal in the event of a system error
     */
    public void parse(Path xmlFile) throws AppError, AppFatal {
        FileInputStream fis;
        BufferedInputStream bis;
        InputStreamReader fir;
        InputSource is;

        // check parameters
        if (xmlFile == null) {
            throw new AppError("Passed null XML file to be processed");
        }

        // Open the VEO for reading
        try {
            fis = new FileInputStream(xmlFile.toFile());
        } catch (FileNotFoundException e) {
            throw new AppError("XML file '" + xmlFile.toString() + "' does not exist");
        }
        bis = new BufferedInputStream(fis);
        // this is necessary because SAX cannot auto-detect the encoding of the XML files
        // it will break if the encoding is not UTF-8
        try {
            fir = new InputStreamReader(bis, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            try {
                fis.close();
            } catch (IOException ioe) {
                /* ignore */ }
            throw new AppFatal("XMLParser.parse(): Error when setting encoding of input file: " + uee.getMessage());
        }
        is = new InputSource(fir);

        // set up parse
        elementsFound = new Stack<>();
        recordElement = false;
        elementBeingCopied = null;
        elementCopy = null;

        // do it...
        try {
            sax.parse(is, this);
        } catch (IOException ioe) {
            throw new AppFatal("XMLParser.parse(): SAX parse of " + xmlFile.toString() + " due to: " + ioe.getMessage());
        } catch (SAXException e) {
            if (!e.getMessage().equals("Finished Parse")) {
                throw new AppError("XMLParser.parse(): SAX parse of " + xmlFile.toString() + " due to: " + e.getMessage());
            }
        } finally {
            elementsFound = null;
            try {
                fir.close();
            } catch (IOException ioe) {
                /* ignore */ }
            try {
                bis.close();
            } catch (IOException ioe) {
                /* ignore */ }
            try {
                fis.close();
            } catch (IOException ioe) {
                /* ignore */ }
        }
    }

    /**
     * This forces the parser to ignore the reference to the external DTD (if
     * any is present)
     */
    private static final ByteArrayInputStream BAIS = new ByteArrayInputStream("".getBytes());

    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException {
        return new InputSource(BAIS);
    }

    /**
     * Start of element
     *
     * This event is called when the SAX parser finds a new element. The element
     * name is pushed onto the stack. The stack keeps the element path from the
     * root of the parse tree to the current element.
     *
     * @param uri
     * @param localName
     * @param qName
     * @param attributes
     * @throws SAXException
     */
    @Override
    public void startElement(String uri, String localName,
            String qName, Attributes attributes)
            throws SAXException {
        int i;
        String s, eFound;
        Element e;

        // push element name onto stack
        e = new Element(qName);
        elementsFound.push(e);

        // get a string representing the path of the current element
        eFound = elementsFound.get(0).elementName;
        for (i = 1; i < elementsFound.size(); i++) {
            eFound += ("/" + elementsFound.get(i).elementName);
        }

        // notify consumer that we have a new element, and ask what to do with value
        he = consumer.startElement(eFound, attributes);

        // if we attempted to capture the value of a non leaf element, ignore what we have captured
        if (elementValue != null) {
            elementValue.setLength(0);
            elementValue = null;
        }

        // if we are to do something with the value...
        if (he != null) {
            switch (he.actionRequested()) {

                // if capturing the value into a file
                case HandleElement.VALUE_TO_STRING:
                    elementValue = new StringBuilder();
                    break;

                // if capturing value into a file, open the output file
                case HandleElement.VALUE_TO_FILE:
                    Path p = he.getOutputFile();
                    try {
                        encfos = new FileOutputStream(p.toFile());
                    } catch (FileNotFoundException fnfe) {
                        throw new SAXException("XMLParser.startElement(): Could not create '" + p.toString() + "' because: " + fnfe.getMessage());
                    }
                    encbos = new BufferedOutputStream(encfos);

                    // if not decoding to Base64, open a writer on top of the file
                    if (!he.isDecodeBase64()) {
                        encosw = new OutputStreamWriter(encbos);
                    } else {
                        encosw = null;
                    }
                    break;

                // if capturing the whole element into a StringBuilder
                case HandleElement.ELEMENT_TO_STRING:
                    recordElement = true;
                    elementBeingCopied = eFound;
                    elementCopy = new StringBuilder();
                    break;
            }
        }

        // if recording this element (or a parent element), record the start
        // element tag and its attributes
        if (recordElement) {
            elementCopy.append("<");
            elementCopy.append(qName);
            if (attributes != null) {
                for (i = 0; i < attributes.getLength(); i++) {
                    elementCopy.append(" ");
                    elementCopy.append(attributes.getQName(i));
                    elementCopy.append("=\"");
                    s = attributes.getValue(i);
                    elementCopy.append(xmlEncode(s));
                    elementCopy.append("\"");
                }
            }
            elementCopy.append(">");
        }
    }

    /**
     * Processing the content of an element
     *
     * Remember the element content if we are interested in the element
     *
     * @param ch
     * @param start
     * @param length
     * @throws org.xml.sax.SAXException
     */
    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        int i;

        // record the characters if capturing an element. But DON'T capture
        // the characters if the value is being captured to a file
        if (recordElement && !(he != null && he.actionRequested() == HandleElement.VALUE_TO_FILE)) {
            for (i = start; i < start + length; i++) {
                switch (ch[i]) {
                    case '&':
                        elementCopy.append("&amp;");
                        break;
                    case '<':
                        elementCopy.append("&lt;");
                        break;
                    case '>':
                        elementCopy.append("&gt;");
                        break;
                    case '"':
                        elementCopy.append("&quot;");
                        break;
                    case '\'':
                        elementCopy.append("&apos;");
                        break;
                    default:
                        elementCopy.append(ch[i]);
                        break;
                }
            }
        }

        // if we are to return the value to the consumer when the end element is seen,
        // store the value (decoding Base64 if necessary)
        if (he != null && he.actionRequested() == HandleElement.VALUE_TO_STRING) {
            if (he.isDecodeBase64()) {
                try {
                    b64.fromBase64(ch, start, length, elementValue);
                } catch (IOException ioe) {
                    throw new SAXException("XMLParser.characters(): failed decoding Base64 to String because: " + ioe.getMessage());
                }
            } else {
                elementValue.append(ch, start, length);
            }
            return;
        }

        // if we are writing the value to the file (decoding Base64 if necessary)
        if (he != null && he.actionRequested() == HandleElement.VALUE_TO_FILE) {
            if (he.isDecodeBase64()) {
                try {
                    b64.fromBase64(ch, start, length, encbos);
                } catch (IOException ioe) {
                    throw new SAXException("XMLParser.characters(): failed decoding Base64 to File because: " + ioe.getMessage());
                }
            } else {
                try {
                    encosw.write(ch, start, length);
                } catch (IOException ioe) {
                    throw new SAXException("XMLParser.characters(): failed writing value to file because: " + ioe.getMessage());
                }
            }
        }
    }

    /**
     * Processing the content of a comment
     *
     * Assume comment is in the content of a value. Only record this if we are
     * recording an element
     *
     * @param ch
     * @param start
     * @param length
     * @throws org.xml.sax.SAXException
     */
    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        if (recordElement) {
            elementCopy.append("<!-- ");
            elementCopy.append(ch, start, length);
            elementCopy.append(" -->");
        }
    }

    /**
     * End of an element
     *
     * Found the end of an element. If recording a value, recording. If
     * recording an element (and this is matching end tag), stop recording the
     * element. Return the value and the element recorded (one or both may be
     * null).
     *
     * @param uri
     * @param localName
     * @param qName
     * @throws org.xml.sax.SAXException
     */
    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        String eFound;
        int i;
        String value, element;

        // get a string representing the path of the current tag
        eFound = elementsFound.get(0).elementName;
        for (i = 1; i < elementsFound.size(); i++) {
            eFound += ("/" + elementsFound.get(i).elementName);
        }

        // pop off the top of the stack
        elementsFound.pop();

        // default is not to return anything
        element = null;
        value = null;

        // If we are recording an element, record the end tag and value.
        if (recordElement) {
            if (he != null && he.actionRequested() == HandleElement.VALUE_TO_FILE) {
                elementCopy.append("[Value saved to record content file]");
            }

            elementCopy.append("</");
            elementCopy.append(qName);
            elementCopy.append(">");

            // if we have seen the end tag of the element being recorded, stop
            if (eFound.equals(elementBeingCopied)) {
                recordElement = false;
                element = elementCopy.toString();
                elementCopy.setLength(0);
                elementCopy = null;
            }
        }

        // If we are recording this value, return it. An empty or blank value is null
        if (he != null && he.actionRequested() == HandleElement.VALUE_TO_STRING) {
            value = elementValue.toString().trim();
            if (value != null && (value.equals("") || value.equals(" "))) {
                value = null;
            }
        }

        // if we are outputing this value to a file, close the file
        if (he != null && he.actionRequested() == HandleElement.VALUE_TO_FILE) {
            if (!he.isDecodeBase64()) {
                try {
                    encosw.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
            try {
                encbos.close();
            } catch (IOException ioe) {
                /* ignore */
            }
            try {
                encfos.close();
            } catch (IOException ioe) {
                /* ignore */
            }
        }

        // tell the consumer what we found
        consumer.endElement(eFound, value, element);

        // stop recording a value
        he = null;
        if (elementValue != null) {
            elementValue.setLength(0);
            elementValue = null;

        }
    }

    /**
     * This private class represents an element on the stack of elements
     * recognised.
     */
    private class Element {

        String elementName; // name of the element
        StringBuilder value;// character value in the element
        boolean noChildElements; // true if this element had no subelements

        public Element(String name) {
            elementName = name;
            value = new StringBuilder();
            noChildElements = true;
        }

        public void free() {
            elementName = null;
            value.setLength(0);
            value = null;
        }
    }

    /**
     * XML encode string
     *
     * Make sure any XML special characters in a string are encoded
     *
     * @param in the String to encode
     * @return the encoded string
     */
    public String xmlEncode(String in) {
        StringBuffer out;
        int i;
        char c;

        if (in == null) {
            return null;
        }
        out = new StringBuffer();
        for (i = 0; i < in.length(); i++) {
            c = in.charAt(i);
            switch (c) {
                case '&':
                    if (!in.regionMatches(true, i, "&amp;", 0, 5)
                            && !in.regionMatches(true, i, "&lt;", 0, 4)
                            && !in.regionMatches(true, i, "&gt;", 0, 4)
                            && !in.regionMatches(true, i, "&quot;", 0, 6)
                            && !in.regionMatches(true, i, "&apos;", 0, 6)) {
                        out.append("&amp;");
                    }
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    out.append(c);
                    break;
            }
        }
        return (out.toString());
    }
}
