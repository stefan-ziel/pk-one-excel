/* $Id: JSONTools.java 1525 2016-12-16 18:52:21Z zis $ */

package br.com.claninfo.pkone.util;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

import ch.claninfo.json.AbstractJSONParser;
import ch.claninfo.json.JSON2DOM;
import ch.claninfo.json.JSONParseException;

/**
 * 
 */
public class JSONTools {

	private static final Logger LOGGER = Logger.getLogger(JSONTools.class.getName());

	// Constants
	private static final String NULL = "null"; //$NON-NLS-1$
	private static final String PROTOCOL_VERSION = "0.2"; //$NON-NLS-1$
	private static Validator validator;

	private JSONTools() {}

	/**
	 * transform a clan date to a json date
	 * 
	 * @param pString clan date
	 * @return json date
	 */
	static public String dateFormat(String pString) {
		if (pString.length() == 10 && pString.charAt(2) == '.') {
			return pString.substring(6, 4) + '-' + pString.substring(3, 2) + '-' + pString.substring(0, 2);
		}
		if (pString.length() == 19 && pString.charAt(2) == '.') {
			return pString.substring(6, 4) + '-' + pString.substring(3, 2) + '-' + pString.substring(0, 2) + 'T' + pString.substring(10, 8);
		}
		return pString;
	}

	/**
	 * validate a JSON document
	 * 
	 * @param pName document name
	 * @param pContent document content
	 * @param pOut
	 * @return success
	 * @throws IOException
	 */
	static public boolean validate(String pName, String pContent, Writer pOut) throws IOException {
		try {
			if (validator == null) {
				SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
				Schema schema = factory.newSchema(JSONTools.class.getResource("/ch/claninfo/pkone/pkOne-Meldungen.xsd")); //$NON-NLS-1$
				validator = schema.newValidator();
			}
			validator.validate(new DOMSource(JSON2DOM.parse(new StringReader(pContent), pName)));
			return true;
		}
		catch (SAXException | JSONParseException e) {
			LOGGER.info("Invalid message " + pName + '\n' + pContent + '\n' + e.getMessage()); //$NON-NLS-1$
			JSONTools.writeStart(pOut);
			JSONTools.writeVersion(pOut);
			JSONTools.writeError(e, pOut);
			JSONTools.writeEnd(pOut);
			return false;
		}
	}

	/**
	 * write closing brace
	 * 
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeArrayEnd(Writer pOut) throws IOException {
		pOut.write(']');
	}

	/**
	 * write name and colon and opening brace
	 * 
	 * @param pName
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeArrayStart(String pName, Writer pOut) throws IOException {
		writeString(pName, pOut);
		pOut.write(':');
		pOut.write('[');
	}

	/**
	 * write closing brace
	 * 
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeEnd(Writer pOut) throws IOException {
		pOut.write('}');
	}

	/**
	 * write an error
	 * 
	 * @param pError
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeError(Throwable pError, Writer pOut) throws IOException {
		LOGGER.log(Level.SEVERE, pError.getMessage(), pError);
		writeSep(pOut);
		writeStart("Fehler", pOut); //$NON-NLS-1$
		writeProp("Meldung", pError.getMessage(), pOut); //$NON-NLS-1$
		writeEnd(pOut);
	}

	/**
	 * write a complete name value pair
	 * 
	 * @param pName
	 * @param pValue
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeProp(String pName, Object pValue, Writer pOut) throws IOException {
		writeString(pName, pOut);
		pOut.write(':');
		if (pValue == null) {
			pOut.write(NULL);
		} else if (pValue instanceof Number) {
			pOut.write(pValue.toString());
		} else if (pValue instanceof Boolean) {
			pOut.write(pValue.toString());
		} else if (pValue instanceof Date) {
			writeString(AbstractJSONParser.STD_DATE_FORMAT.format(pValue), pOut);
		} else {
			writeString(pValue.toString(), pOut);
		}

	}

	/**
	 * write comma an new line
	 * 
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeSep(Writer pOut) throws IOException {
		pOut.write(',');
		pOut.write('\n');
	}

	/**
	 * write name and colon and opening brace
	 * 
	 * @param pName
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeStart(String pName, Writer pOut) throws IOException {
		writeString(pName, pOut);
		pOut.write(':');
		writeStart(pOut);
	}

	/**
	 * write opening brace
	 * 
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeStart(Writer pOut) throws IOException {
		pOut.write('{');
	}

	/**
	 * write a string with quoting
	 *
	 * @param pString
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeString(String pString, Writer pOut) throws IOException {
		if (pString == null) {
			pOut.write(NULL);
			return;
		}
		pOut.write('"');
		for (int i = 0; i < pString.length(); i++) {
			char c = pString.charAt(i);
			if (c == '\"' || c == '\\') {
				pOut.write('\\');
				pOut.write(c);
			} else if (c == '\n') {
				pOut.write('\\');
				pOut.write('n');
			} else if (c == '\t') {
				pOut.write('\\');
				pOut.write('t');
			} else if (c == '\r') {
				pOut.write('\\');
				pOut.write('r');
			} else {
				pOut.write(c);
			}
		}
		pOut.write('"');
	}

	/**
	 * write version info
	 * 
	 * @param pOut
	 * @throws IOException
	 */
	static public void writeVersion(Writer pOut) throws IOException {
		writeProp("Version", PROTOCOL_VERSION, pOut); //$NON-NLS-1$
	}
}
