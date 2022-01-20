/***********************************************************************
 * This file is part of iDempiere ERP Open Source                      *
 * http://www.idempiere.org                                            *
 *                                                                     *
 * Copyright (C) Contributors                                          *
 *                                                                     *
 * This program is free software; you can redistribute it and/or       *
 * modify it under the terms of the GNU General Public License         *
 * as published by the Free Software Foundation; either version 2      *
 * of the License, or (at your option) any later version.              *
 *                                                                     *
 * This program is distributed in the hope that it will be useful,     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of      *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the        *
 * GNU General Public License for more details.                        *
 *                                                                     *
 * You should have received a copy of the GNU General Public License   *
 * along with this program; if not, write to the Free Software         *
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,          *
 * MA 02110-1301, USA.                                                 *
 *                                                                     *
 * Contributors:                                                       *
 * - Carlos Ruiz - globalqss - BX Service                              *
 **********************************************************************/

package de.bxservice.hibiscus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.zip.CRC32;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.impexp.BankStatementLoaderInterface;
import org.compiere.model.MBankAccount;
import org.compiere.model.MBankStatementLoader;
import org.compiere.model.MSysConfig;
import org.compiere.model.X_I_BankStatement;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ParseDate;
import org.supercsv.cellprocessor.ParseInt;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.prefs.CsvPreference;

/**
 * This bank statement loader for iDempiere is developed to import into I_BankStatement
 * a CSV file generated with Hibiscus via a modified velocity template
 * 
 * @author Carlos Ruiz - globalqss - BX Service
 */
public class HibiscusLoader implements BankStatementLoaderInterface {

	/*
	 * SysConfig keys:
	 * BXS_HIBISCUS_STATEMENT_DESCRIPTION - string to fill description in the statement header
	 * BXS_HIBISCUS_VALIDATE_DUPS_UMSATZID - validate if the record is already loaded using the unique umsatzid
	 * BXS_HIBISCUS_VALIDATE_CHECKSUM - validate the checksum
	 * BXS_HIBISCUS_FORCE_CHECKSUM - force the checksum - if true the record cannot be imported when the checksum fails
	 */

	// MAPPING:
	// +-------------------------+------------------+-----------------------+
	// |    Hibiscus DB          |    CSV File      |    I_BankStatement    |
	// +-------------------------+------------------+-----------------------+
	// | konto.kontonummer       | Konto_AccountNo  | BankAccountNo         |
	// +-------------------------+------------------+-----------------------+
	// | konto.bic               | Konto_RoutingNo  | RoutingNo             |
	// |                         |                  | C_BankAccount_ID      |
	// +-------------------------+------------------+-----------------------+
	// | konto.id                | Konto_Id         |                       |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.id               | Umsatz_Id        | EftTrxID              |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.empfaenger_konto | Empfaenger_Konto | EftPayeeAccount       |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.empfaenger_blz   | Empfaenger_Blz   | EftCheckNo            |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.empfaenger_name  | Empfaenger_Name  | EftPayee              |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.betrag           | Betrag           | StmtAmt               |
	// |                         |                  | EftAmt                |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.zweck            | Zweck            | EftMemo               |
	// | umsatz.zweck2           | Zweck2           |                       |
	// | umsatz.zweck3           | Zweck3           |                       |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.datum            | Datum            | StatementLineDate     |
	// |                         |                  | EftStatementLineDate  |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.valuta           | Valuta           | ValutaDate            |
	// |                         |                  | EftValutaDate         |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.kommentar        | Kommentar        | LineDescription       |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.checksum         | Checksum         | TrxType               |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.gvcode           | GvCode           | EftTrxType            |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.endtoendid       | EndToEndId       | EftReference          |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.mandateid        | MandateId        | ReferenceNo           |
	// +-------------------------+------------------+-----------------------+
	// | umsatz.primanota        | PrimaNota        | Memo                  |
	// | umsatz.art              | Art              |                       |
	// | umsatz.customerref      | CustomerRef      |                       |
	// | umsatz.addkey           | AddKey           |                       |
	// | umsatz.txid             | TxId             |                       |
	// | umsatz.purposecode      | PurposeCode      |                       |
	// | umsatz.empfaenger_name2 | Empfaenger_Name2 |                       |
	// | umsatztyp.name          | UmsatzTyp_Name   |                       |
	// +-------------------------+------------------+-----------------------+
	// | Other Fields            | FileName         | EftStatementReference |
	// |                         +------------------+-----------------------+
	// |                         | Load Timestamp   | Name                  |
	// |                         |                  | StatementDate         |
	// |                         +------------------+-----------------------+
	// |                         | SysConfig        | Description           |
	// +-------------------------+------------------+-----------------------+

	private static final String CSVCOLNAME_Konto_AccountNo = "Konto_AccountNo";
	private static final String CSVCOLNAME_Konto_RoutingNo = "Konto_RoutingNo";
	private static final String CSVCOLNAME_Konto_Id = "Konto_Id";
	private static final String CSVCOLNAME_Umsatz_Id = "Umsatz_Id";
	private static final String CSVCOLNAME_Empfaenger_Konto = "Empfaenger_Konto";
	private static final String CSVCOLNAME_Empfaenger_Blz = "Empfaenger_Blz";
	private static final String CSVCOLNAME_Empfaenger_Name = "Empfaenger_Name";
	private static final String CSVCOLNAME_Betrag = "Betrag";
	private static final String CSVCOLNAME_Zweck = "Zweck";
	private static final String CSVCOLNAME_Zweck2 = "Zweck2";
	private static final String CSVCOLNAME_Zweck3 = "Zweck3";
	private static final String CSVCOLNAME_Datum = "Datum";
	private static final String CSVCOLNAME_Valuta = "Valuta";
	private static final String CSVCOLNAME_Kommentar = "Kommentar";
	private static final String CSVCOLNAME_Checksum = "Checksum";
	private static final String CSVCOLNAME_GvCode = "GvCode";
	private static final String CSVCOLNAME_EndToEndId = "EndToEndId";
	private static final String CSVCOLNAME_MandateId = "MandateId";
	private static final String CSVCOLNAME_PrimaNota = "PrimaNota";
	private static final String CSVCOLNAME_Art = "Art";
	private static final String CSVCOLNAME_CustomerRef = "CustomerRef";
	private static final String CSVCOLNAME_AddKey = "AddKey";
	private static final String CSVCOLNAME_TxId = "TxId";
	private static final String CSVCOLNAME_PurposeCode = "PurposeCode";
	private static final String CSVCOLNAME_Empfaenger_Name2 = "Empfaenger_Name2";
	private static final String CSVCOLNAME_UmsatzTyp_Name = "UmsatzTyp_Name";

	private MBankStatementLoader m_bsl;
	private StringBuffer m_errorMessage;
	private StringBuffer m_errorDescription;
	private StatementLine m_line;

	/** Static Logger */
	private static CLogger s_log = CLogger.getCLogger(HibiscusLoader.class);

	@Override
	public boolean init(MBankStatementLoader bsl) {
		if (bsl == null) {
			m_errorMessage = new StringBuffer("ErrorInitializingParser");
			m_errorDescription = new StringBuffer("ImportController is a null reference");
			return false;
		}
		this.m_bsl = bsl;
		return true;
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public boolean loadLines() {

		if (s_log.isLoggable(Level.INFO))
			s_log.info("");

		String statementRef = m_bsl.getLocalFileName();
		int last = statementRef.lastIndexOf("_");
		if (last < 0)
			last = statementRef.lastIndexOf(File.separator);
		if (last > 0)
			statementRef = statementRef.substring(last+1);
		Timestamp loadTS = new Timestamp(System.currentTimeMillis());
		String statementName = loadTS.toString();

		CsvPreference csvpref = CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE;
		Charset charset = Charset.forName("UTF-8");
		ICsvMapReader mapReader = null;
		int cnt = 1;
		try {

			final CellProcessor[] processors = new CellProcessor[] { new NotNull(), // Konto_AccountNo
					new NotNull(), // Konto_RoutingNo
					new NotNull(new ParseInt()), // Konto_Id
					new NotNull(new ParseInt()), // Umsatz_Id
					new Optional(), // Empfaenger_Konto
					new Optional(), // Empfaenger_Blz
					new Optional(), // Empfaenger_Name
					new NotNull(), // Betrag
					new Optional(), // Zweck
					new Optional(), // Zweck2
					new Optional(), // Zweck3
					new ParseDate("dd.MM.yyyy"), // Datum
					new ParseDate("dd.MM.yyyy"), // Valuta
					new Optional(), // Kommentar
					new Optional(new ParseLong()), // Checksum
					new Optional(), // GvCode
					new Optional(), // EndToEndId
					new Optional(), // MandateId
					new Optional(), // PrimaNota
					new Optional(), // Art
					new Optional(), // CustomerRef
					new Optional(), // AddKey
					new Optional(), // TxId
					new Optional(), // PurposeCode
					new Optional(), // Empfaenger_Name2
					new Optional() // UmsatzTyp_Name
			};

			FileInputStream fileInputStream = new FileInputStream(new File(m_bsl.getLocalFileName()));
			mapReader = new CsvMapReader(new InputStreamReader(fileInputStream, charset), csvpref);
			final String[] header = mapReader.getHeader(true);
			Map<String, Object> values;
			while ((values = mapReader.read(header, processors)) != null) {
				cnt++;
				String v_Konto_AccountNo = (String) values.get(CSVCOLNAME_Konto_AccountNo);
				String v_Konto_RoutingNo = (String) values.get(CSVCOLNAME_Konto_RoutingNo);
				Integer v_Konto_Id = (Integer) values.get(CSVCOLNAME_Konto_Id);
				Integer v_Umsatz_Id = (Integer) values.get(CSVCOLNAME_Umsatz_Id);
				String v_Empfaenger_Konto = (String) values.get(CSVCOLNAME_Empfaenger_Konto);
				String v_Empfaenger_Blz = (String) values.get(CSVCOLNAME_Empfaenger_Blz);
				String v_Empfaenger_Name = (String) values.get(CSVCOLNAME_Empfaenger_Name);
				String v_BetragString = (String) values.get(CSVCOLNAME_Betrag);
				String v_Zweck = (String) values.get(CSVCOLNAME_Zweck);
				String v_Zweck2 = (String) values.get(CSVCOLNAME_Zweck2);
				String v_Zweck3 = (String) values.get(CSVCOLNAME_Zweck3);
				Date v_Datum = (Date) values.get(CSVCOLNAME_Datum);
				Date v_Valuta = (Date) values.get(CSVCOLNAME_Valuta);
				String v_Kommentar = (String) values.get(CSVCOLNAME_Kommentar);
				Long v_Checksum = (Long) values.get(CSVCOLNAME_Checksum);
				String v_GvCode = (String) values.get(CSVCOLNAME_GvCode);
				String v_EndToEndId = (String) values.get(CSVCOLNAME_EndToEndId);
				String v_MandateId = (String) values.get(CSVCOLNAME_MandateId);
				String v_PrimaNota = (String) values.get(CSVCOLNAME_PrimaNota);
				String v_Art = (String) values.get(CSVCOLNAME_Art);
				String v_CustomerRef = (String) values.get(CSVCOLNAME_CustomerRef);
				String v_AddKey = (String) values.get(CSVCOLNAME_AddKey);
				String v_TxId = (String) values.get(CSVCOLNAME_TxId);
				String v_PurposeCode = (String) values.get(CSVCOLNAME_PurposeCode);
				String v_Empfaenger_Name2 = (String) values.get(CSVCOLNAME_Empfaenger_Name2);
				String v_UmsatzTyp_Name = (String) values.get(CSVCOLNAME_UmsatzTyp_Name);
				BigDecimal v_Betrag = null;
				// depending on Hibiscus configuration sometimes the decimal separator is sent as comma
				v_BetragString = v_BetragString.replace(",", ".");
				v_Betrag = new BigDecimal(v_BetragString);

				m_line = new StatementLine();
				m_line.bankAccountNo = v_Konto_AccountNo;
				m_line.routingNo = v_Konto_RoutingNo;
				m_line.trxID = String.valueOf(v_Umsatz_Id);
				m_line.payeeAccountNo = v_Empfaenger_Konto;
				m_line.checkNo = v_Empfaenger_Blz;
				m_line.payeeName = v_Empfaenger_Name;
				m_line.stmtAmt = v_Betrag;
				m_line.trxAmt = v_Betrag;
				m_line.statementLineDate = new Timestamp(v_Datum.getTime());
				m_line.valutaDate = new Timestamp(v_Valuta.getTime());
				m_line.trxType = String.valueOf(v_Checksum);
				m_line.reference = v_EndToEndId;
				m_line.statementReference = statementRef;
				// m_line.statementDate
				// m_line.isReversal
				// m_line.currency
				// m_line.chargeName
				// m_line.chargeAmt
				// m_line.interestAmt
				// m_line.iban

				StringBuilder mergedzweck = new StringBuilder();
				if (v_Zweck != null)
					mergedzweck.append(v_Zweck);
				if (v_Zweck2 != null)
					mergedzweck.append("\n").append(v_Zweck2);
				if (v_Zweck3 != null)
					mergedzweck.append("\n").append(v_Zweck3);
				m_line.memo = mergedzweck.toString();

				StringBuilder memo2 = new StringBuilder();
				append(memo2, "PrimaNota", v_PrimaNota);
				append(memo2, "Art", v_Art);
				append(memo2, "CustomerRef", v_CustomerRef);
				append(memo2, "AddKey", v_AddKey);
				append(memo2, "TxId", v_TxId);
				append(memo2, "PurposeCode", v_PurposeCode);
				append(memo2, "Empfaenger_Name2", v_Empfaenger_Name2);
				append(memo2, "UmsatzTyp_Name", v_UmsatzTyp_Name);

				// create the I_BankStatement record
				if (!m_bsl.saveLine())
					return false;

				X_I_BankStatement ibs = m_bsl.getLastSavedLine();
				ibs.setMemo(memo2.toString());
				ibs.setLineDescription(v_Kommentar);
				ibs.setEftTrxType(v_GvCode);
				ibs.setReferenceNo(v_MandateId);
				ibs.setName(statementName);
				ibs.setDescription(MSysConfig.getValue("BXS_HIBISCUS_STATEMENT_DESCRIPTION", "Uploaded via de.bxservice.hibiscus.HibiscusLoader", Env.getAD_Client_ID(Env.getCtx())));
				ibs.setStatementDate(loadTS);
				ibs.saveEx();

				// Verify that bank account was found
				if (ibs.getC_BankAccount_ID() <= 0) {
					m_errorMessage = new StringBuffer("LoadError");
					m_errorDescription = new StringBuffer("Bank Not Found Account=").append(v_Konto_AccountNo).append(", Routing=").append(v_Konto_RoutingNo);
					return false;
				}
				MBankAccount ba = MBankAccount.get(ibs.getC_BankAccount_ID());
				// verify there is no record with same Umsatz_Id (Line) in I_BankStatement or C_BankStatementLine
				if (MSysConfig.getBooleanValue("BXS_HIBISCUS_VALIDATE_DUPS_UMSATZID", true, Env.getAD_Client_ID(Env.getCtx()))) {
					final String sqlcntibs = "SELECT COUNT(*) FROM I_BankStatement WHERE Line=? AND C_BankAccount_ID=? AND I_BankStatement_ID!=?";
					int cntibs = DB.getSQLValueEx(m_bsl.get_TrxName(), sqlcntibs, v_Umsatz_Id, ba.getC_BankAccount_ID(), ibs.getI_BankStatement_ID());
					if (cntibs > 0) {
						m_errorMessage = new StringBuffer("LoadError");
						m_errorDescription = new StringBuffer("The line ").append(v_Umsatz_Id).append(" was already loaded in I_BankStatementLine");
						return false;
					}
					final String sqlcntbs = "SELECT Name FROM C_BankStatementLine bsl JOIN C_BankStatement bs ON (bsl.C_BankStatement_ID=bs.C_BankStatement_ID) WHERE bsl.Line=? AND bs.C_BankAccount_ID=?";
					String bsname = DB.getSQLValueStringEx(m_bsl.get_TrxName(), sqlcntbs, v_Umsatz_Id, ba.getC_BankAccount_ID());
					if (bsname != null) {
						m_errorMessage = new StringBuffer("LoadError");
						m_errorDescription = new StringBuffer("The line ").append(v_Umsatz_Id).append(" was already loaded in Bank Statement ").append(bsname);
						return false;
					}
				}
				// validate checksum to avoid tampering of the data
				if (MSysConfig.getBooleanValue("BXS_HIBISCUS_VALIDATE_CHECKSUM", false, Env.getAD_Client_ID(Env.getCtx()))) {
					long calcCheckssum = calcCheckSum(v_Art, v_Konto_Id, v_Betrag.doubleValue(), v_CustomerRef,
							v_Empfaenger_Blz, v_Empfaenger_Konto, v_Empfaenger_Name, v_PrimaNota, mergedzweck.toString(), v_Datum,
							v_Valuta);
					if (calcCheckssum != v_Checksum.longValue()) {
						m_errorDescription = new StringBuffer("The checksum for line ").append(v_Umsatz_Id)
								.append(" doesn't match, calculated=").append(calcCheckssum).append(", expected=")
								.append(v_Checksum.longValue());
						if (MSysConfig.getBooleanValue("BXS_HIBISCUS_FORCE_CHECKSUM", false, Env.getAD_Client_ID(Env.getCtx()))) {
							m_errorMessage = new StringBuffer("LoadError");
							return false;
						} else {
							s_log.warning(m_errorDescription.toString());
						}
					}
				}
			}
		} catch (Exception e) {
			m_errorMessage = new StringBuffer("LoadError");
			m_errorDescription = new StringBuffer(Msg.getElement(Env.getCtx(), "Line")).append(" ").append(cnt).append(" -> ");
			if (e.getLocalizedMessage() != null)
				m_errorDescription.append(e.getLocalizedMessage());
			else if (e.getMessage() != null)
				m_errorDescription.append(e.getMessage());
			else
				m_errorDescription.append(e.toString());
			return false;
		} finally {
			if (mapReader != null) {
				try {
					mapReader.close();
				} catch (IOException e) {
					throw new AdempiereException(e);
				}
			}
		}

		return true;
	}

	/**
	 * Calculate the checksum of the record
	 * Based on Hibiscus method de.willuhn.jameica.hbci.server.UmsatzImpl.getChecksum()
	 * @param art
	 * @param kontoid
	 * @param betrag
	 * @param customerref
	 * @param gegenkontoBLZ
	 * @param gegenkontoNummer
	 * @param gegenkontoName
	 * @param primanota
	 * @param mergedzweck
	 * @param datum
	 * @param valuta
	 * @return
	 */
	private long calcCheckSum(String art, int kontoid, double betrag, String customerref,
			String gegenkontoBLZ, String gegenkontoNummer, String gegenkontoName, String primanota,
			String mergedzweckNL, Date datum, Date valuta) {
		// TODO: This method still doesn't return the exact same value as Hibiscus, it requires more debugging
		DateFormat HBCI_DATEFORMAT = new SimpleDateFormat("dd.MM.yyyy");
		String sd = HBCI_DATEFORMAT.format(datum);
		String sv = HBCI_DATEFORMAT.format(valuta);
		// based on Hibiscus method de.willuhn.jameica.hbci.server.UmsatzImpl.getAttribute("mergedzweck")
		String mergedzweck = "";
		List<String> mz = new ArrayList<String>();
		if (mergedzweckNL != null) {
			for (String z3 : mergedzweckNL.split("\n"))
				mz.add(z3);
			for (String s : mz) {
				if (mergedzweck.length() > 0)
					mergedzweck += " ";
				if (s != null)
					mergedzweck += s;
			}
			mergedzweck = mergedzweckNL.replace("\n", " ");
		}
		if (art == null)
			art = "";
		String s = (""+art.toUpperCase() +
				kontoid + // wenigstens die ID vom Konto muss mit rein. Andernfalls haben zwei gleich aussehende Umsaetze auf verschiedenen Konten die gleiche Checksumme
				betrag +
				customerref +
				gegenkontoBLZ +
				gegenkontoNummer +
				(""+gegenkontoName).toUpperCase() +
				primanota +
				"" +   // <- saldo not included
				mergedzweck.toUpperCase() +
				sd +
				sv);
		CRC32 crc = new CRC32();
		crc.update(s.getBytes());
		return crc.getValue();
	}

	private void append(StringBuilder sb, String name, String var) {
		if (!Util.isEmpty(var)) {
			if (sb.length() > 0)
				sb.append("\n");
			sb.append(name).append("=").append(var);
		}
	}

	@Override
	public String getLastErrorMessage() {
		return m_errorMessage.toString();
	}

	@Override
	public String getLastErrorDescription() {
		return m_errorDescription.toString();
	}

	/**
	 * Does not acquire data
	 */
	@Override
	public Timestamp getDateLastRun() {
		return null;
	}

	@Override
	public String getRoutingNo() {
		return m_line.routingNo;
	}

	@Override
	public String getBankAccountNo() {
		return m_line.bankAccountNo;
	}

	@Override
	public String getIBAN() {
		return m_line.iban;
	}

	@Override
	public String getStatementReference() {
		return m_line.statementReference;
	}

	@Override
	public Timestamp getStatementDate() {
		return m_line.statementDate;
	}

	@Override
	public String getTrxID() {
		return m_line.trxID;
	}

	@Override
	public String getReference() {
		return m_line.reference;
	}

	@Override
	public String getCheckNo() {
		return m_line.checkNo;
	}

	@Override
	public String getPayeeName() {
		return m_line.payeeName;
	}

	@Override
	public String getPayeeAccountNo() {
		return m_line.payeeAccountNo;
	}

	@Override
	public Timestamp getStatementLineDate() {
		return m_line.statementLineDate;
	}

	@Override
	public Timestamp getValutaDate() {
		return m_line.valutaDate;
	}

	@Override
	public String getTrxType() {
		return m_line.trxType;
	}

	@Override
	public boolean getIsReversal() {
		return m_line.isReversal;
	}

	@Override
	public String getCurrency() {
		return m_line.currency;
	}

	@Override
	public BigDecimal getStmtAmt() {
		return m_line.stmtAmt;
	}

	@Override
	public BigDecimal getTrxAmt() {
		return m_line.trxAmt;
	}

	@Override
	public BigDecimal getInterestAmt() {
		return m_line.interestAmt;
	}

	@Override
	public String getMemo() {
		return m_line.memo;
	}

	@Override
	public String getChargeName() {
		return m_line.chargeName;
	}

	@Override
	public BigDecimal getChargeAmt() {
		return m_line.chargeAmt;
	}

	static class StatementLine {
		protected String routingNo = null;
		protected String bankAccountNo = null;
		protected String statementReference = null;
		protected Timestamp statementDate = null;
		protected Timestamp statementLineDate = null;

		protected String reference = null;
		protected Timestamp valutaDate;
		protected String trxType = null;
		protected boolean isReversal = false;
		protected String currency = null;
		protected BigDecimal stmtAmt = null;
		protected BigDecimal trxAmt = null;
		protected String memo = null;
		protected String chargeName = null;
		protected BigDecimal chargeAmt = null;
		protected String payeeAccountNo = null;
		protected String payeeName = null;
		protected String trxID = null;
		protected String checkNo = null;
		protected BigDecimal interestAmt = null;
		protected String iban = null;
	}

}
