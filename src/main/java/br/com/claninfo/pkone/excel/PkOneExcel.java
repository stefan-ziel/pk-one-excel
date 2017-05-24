/* $Id: PkOneServlet.java 1590 2017-05-09 16:28:34Z zis $ */

package br.com.claninfo.pkone.excel;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;

import br.com.claninfo.pkone.util.JSONTools;
import ch.claninfo.json.JSONParser;

/**
 * Transform json-requests to workfow calls
 */
public class PkOneExcel extends HttpServlet {

	private static final String VALUE = "Value"; //$NON-NLS-1$
	private static final String RANGE = "Range"; //$NON-NLS-1$
	// XML Attributes
	private static final String TRUE = "true"; //$NON-NLS-1$

	static final ExcelCellInfo[] INPUT_MAP = new ExcelCellInfo[] {new ExcelCellInfo("Geburtsdatum", 1, "B6"), // //$NON-NLS-1$ //$NON-NLS-2$
																																new ExcelCellInfo("Mitnum", 1, "B9"), // //$NON-NLS-1$ //$NON-NLS-2$
																																new ExcelCellInfo("Sparplan", 1, "B10", "Standard"), // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
																																new ExcelCellInfo("Stichtag", 1, "B12", date(2017, 12, 1)), // //$NON-NLS-1$ //$NON-NLS-2$
																																new ExcelCellInfo("Jahreslohn", 1, "B13"), // //$NON-NLS-1$ //$NON-NLS-2$
																																new ExcelCellInfo("Sparkapital", 1, "B14"), // //$NON-NLS-1$ //$NON-NLS-2$
																																new ExcelCellInfo("PensDatVor", 1, "B17", date(2017, 12, 31)), // //$NON-NLS-1$ //$NON-NLS-2$
																																new ExcelCellInfo("PensDatNach", 1, "B18", date(2019, 1, 1)), // //$NON-NLS-1$ //$NON-NLS-2$
																																new ExcelCellInfo("Projektionszinssatz", 1, "B19", Double.valueOf(.01))// //$NON-NLS-1$ //$NON-NLS-2$
	};
	static final ExcelCellInfo[] OUTPUT_MAP = new ExcelCellInfo[] {	new ExcelCellInfo("EndkapitalNach", 1, "K33"), // //$NON-NLS-1$ //$NON-NLS-2$
																																	new ExcelCellInfo("EndkapitalVor", 1, "L33"), // //$NON-NLS-1$ //$NON-NLS-2$
																																	new ExcelCellInfo("UmwandlungssatzNach", 1, "K36"), // //$NON-NLS-1$ //$NON-NLS-2$
																																	new ExcelCellInfo("UmwandlungssatzVor", 1, "L36"), // //$NON-NLS-1$ //$NON-NLS-2$
																																	new ExcelCellInfo("AltersrenteNach", 1, "K37"), // //$NON-NLS-1$ //$NON-NLS-2$
																																	new ExcelCellInfo("AltersrenteVor", 1, "L37"), // //$NON-NLS-1$ //$NON-NLS-2$
																																	new ExcelCellInfo("AltersrenteNachProz", 1, "K38"), // //$NON-NLS-1$ //$NON-NLS-2$
																																	new ExcelCellInfo("AltersrenteVorProz", 1, "L38") // //$NON-NLS-1$ //$NON-NLS-2$
	};
	Logger logger;

	int validate;
	int globalProcessId = 0;

	Dispatch workbook;
	Dispatch globalSheets;

	static Date date(int year, int month, int date) {
		Calendar working = Calendar.getInstance();
		working.set(year, month - 1, date, 0, 0, 1);
		return working.getTime();
	}

	/**
	 * @see javax.servlet.GenericServlet#destroy()
	 */
	@Override
	public void destroy() {
		if (workbook != null) {
			Dispatch.call(workbook, "Close", new Variant(false)); //$NON-NLS-1$
		}
		super.destroy();
	}

	@Override
	public void init() throws ServletException {
		super.init();
		logger = Logger.getLogger(PkOneExcel.class.getName());
	}

	@Override
	protected void service(HttpServletRequest pReq, HttpServletResponse pResp) throws ServletException, IOException {
		pResp.setContentType("application/json"); //$NON-NLS-1$
		Writer out;
		Reader in;
		String requestname = pReq.getPathInfo().substring(1);

		logger.info("Start " + requestname); //$NON-NLS-1$
		if (doValidate()) {
			String request = readAll(pReq.getReader());
			if (!JSONTools.validate(requestname + "Request", request, pResp.getWriter())) { //$NON-NLS-1$
				pResp.setStatus(500);
				return;
			}
			in = new StringReader(request);
			out = new StringWriter();
		} else {
			in = pReq.getReader();
			out = pResp.getWriter();
		}
		JSONTools.writeStart(out);
		JSONTools.writeVersion(out);
		try {
			Map<String, Object> jsonRequest;
			Integer prozessId = Integer.valueOf(0);
			String language = "de"; //$NON-NLS-1$
			try {
				jsonRequest = JSONParser.parse(in);
				language = (String) jsonRequest.remove("Sprache"); //$NON-NLS-1$

				if (requestname.endsWith("Start")) { //$NON-NLS-1$
					prozessId = Integer.valueOf(globalProcessId++);
				} else {
					prozessId = Integer.valueOf(((Number) jsonRequest.remove("ProzessID")).intValue()); //$NON-NLS-1$
				}
			}
			finally {
				JSONTools.writeSep(out);
				JSONTools.writeProp("Sprache", language, out); //$NON-NLS-1$
				JSONTools.writeSep(out);
				JSONTools.writeProp("ProzessID", prozessId, out); //$NON-NLS-1$
			}
			processRequest(jsonRequest, out);
		}
		catch (Throwable e) {
			JSONTools.writeError(e, out);
			pResp.setStatus(500);
		}
		JSONTools.writeEnd(out);
		if (doValidate()) {
			String response = ((StringWriter) out).toString();
			if (!JSONTools.validate(requestname + "Response", response, pResp.getWriter())) { //$NON-NLS-1$
				pResp.setStatus(500);
				return;
			}
			pResp.getWriter().write(response);
		}
		logger.info("End " + requestname); //$NON-NLS-1$
	}

	synchronized boolean doValidate() {
		if (validate == 0) {
			validate = TRUE.equals(getConfig("validate", "false")) ? 1 : -1; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return validate == 1;
	}

	Object getConfig(String pKey, Object pDefault) {
		Object res;
		StringBuffer key = new StringBuffer("br.com.claninfo.pkone."); //$NON-NLS-1$
		key.append(pKey);
		try {
			Context initContext = (Context) new InitialContext().lookup("java:comp/env"); //$NON-NLS-1$ ;
			res = initContext.lookup(key.toString());
		}
		catch (NamingException ne) {
			res = null;
		}
		if (res == null) {
			if (pDefault == null) {
				throw new IllegalArgumentException("Undefined environment " + key.toString()); //$NON-NLS-1$
			}
			res = pDefault;
		}
		return res;
	}

	Dispatch getSheets() {
		if (globalSheets == null) {
			String fileName = (String) getConfig("excel", null); //$NON-NLS-1$
			ActiveXComponent xl = ActiveXComponent.connectToActiveInstance("Excel.Application"); //$NON-NLS-1$
			if (xl == null) {
				xl = ActiveXComponent.createNewInstance("Excel.Application"); //$NON-NLS-1$
			}
			Dispatch workbooks = xl.getProperty("Workbooks").toDispatch(); //$NON-NLS-1$
			workbook = Dispatch.call(workbooks, "Open", new Variant(fileName)).toDispatch(); //$NON-NLS-1$
			globalSheets = Dispatch.get(workbook, "Worksheets").toDispatch(); //$NON-NLS-1$
		}
		return globalSheets;
	}

	void processRequest(Map<String, Object> pJsonRequest, Writer pOut) throws IOException {
		Dispatch sheets = getSheets();
		for (ExcelCellInfo info : INPUT_MAP) {
			Dispatch sheet = Dispatch.invoke(sheets, "Item", Dispatch.Get, new Object[] {info.getSheetNumber()}, new int[1]).toDispatch(); //$NON-NLS-1$
			Dispatch cell = Dispatch.invoke(sheet, RANGE, Dispatch.Get, new Object[] {info.getCell()}, new int[1]).toDispatch();
			Object value = pJsonRequest.get(info.getJsonName());
			if (value == null) {
				value = info.getDefault();
			}
			Dispatch.put(cell, VALUE, value);
		}

		JSONTools.writeSep(pOut);
		JSONTools.writeArray("Simulation", pOut); //$NON-NLS-1$
		boolean first = true;
		for (ExcelCellInfo info : OUTPUT_MAP) {
			Dispatch sheet = Dispatch.invoke(sheets, "Item", Dispatch.Get, new Object[] {info.getSheetNumber()}, new int[1]).toDispatch(); //$NON-NLS-1$
			Dispatch cell = Dispatch.invoke(sheet, RANGE, Dispatch.Get, new Object[] {info.getCell()}, new int[1]).toDispatch();
			Object value = Dispatch.get(cell, VALUE);
			if (first) {
				first = false;
			} else {
				JSONTools.writeSep(pOut);
			}
			JSONTools.writeStart(pOut);
			JSONTools.writeProp("Name", info.getJsonName(), pOut); //$NON-NLS-1$
			JSONTools.writeSep(pOut);
			JSONTools.writeProp("Wert", value, pOut); //$NON-NLS-1$
			JSONTools.writeEnd(pOut);
		}
		pOut.write(']');
	}

	String readAll(Reader pIn) throws IOException {
		StringBuffer content = new StringBuffer();
		char[] buff = new char[255];
		int read = pIn.read(buff, 0, buff.length);
		while (read >= 0) {
			content.append(buff, 0, read);
			read = pIn.read(buff, 0, buff.length);
		}
		return content.toString();
	}

	static class ExcelCellInfo {

		String jsonName;
		int sheetNumber;
		String cell;
		Object def;

		public ExcelCellInfo(String pJsonName, int pSheetNumber, String pCell) {
			this(pJsonName, pSheetNumber, pCell, null);
		}

		public ExcelCellInfo(String pJsonName, int pSheetNumber, String pCell, Object pDef) {
			super();
			jsonName = pJsonName;
			sheetNumber = pSheetNumber;
			cell = pCell;
		}

		/**
		 * @return the cell
		 */
		public String getCell() {
			return cell;
		}

		public Object getDefault() {
			return def;
		}

		/**
		 * @return the jsonName
		 */
		public String getJsonName() {
			return jsonName;
		}

		Integer getSheetNumber() {
			return Integer.valueOf(sheetNumber);
		}
	}

}
