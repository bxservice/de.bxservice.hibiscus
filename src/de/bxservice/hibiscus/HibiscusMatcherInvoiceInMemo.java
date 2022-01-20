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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.impexp.BankStatementMatchInfo;
import org.compiere.impexp.BankStatementMatcherInterface;
import org.compiere.model.MBankStatementLine;
import org.compiere.model.MInvoice;
import org.compiere.model.MSysConfig;
import org.compiere.model.Query;
import org.compiere.model.X_I_BankStatement;
import org.compiere.util.CLogger;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 * This is a basic bank statement matcher that searches for invoice number in the EftMemo field
 * it verifies if the amount is less than the pending from the invoice
 * 
 * @author Carlos Ruiz - globalqss - BX Service
 */
public class HibiscusMatcherInvoiceInMemo implements BankStatementMatcherInterface {

	/**	Logger							*/
	protected CLogger			log = CLogger.getCLogger (getClass());

	/**
	 * 	Match Bank Statement Line
	 *	@param bsl bank statement line
	 *	@return found matches or null
	 */
	@Override
	public BankStatementMatchInfo findMatch(MBankStatementLine bsl) {
		BankStatementMatchInfo bsi = new BankStatementMatchInfo();
		if (bsl.getTrxAmt().signum() != 0)
			matchInvoiceInMemo(bsi, bsl);
		return bsi;
	}

	private void matchInvoiceInMemo(BankStatementMatchInfo bsi, MBankStatementLine bsl) {

		if (bsl.getTrxAmt().signum() > 0) {

			// match customer invoices
			List<String> potentialInvoices = searchInvoiceInMemo(bsl.getEftMemo());
			List<MInvoice> invoices = new ArrayList<MInvoice>();
			for (String potentialInvoice : potentialInvoices) {
				MInvoice invoice = new Query(bsl.getCtx(), MInvoice.Table_Name, "DocumentNo=? AND IsSOTrx='Y' AND DocStatus IN ('CO','CL','WP')", bsl.get_TrxName())
						.setOnlyActiveRecords(true)
						.setClient_ID()
						.setParameters(potentialInvoice)
						.first();
				if (invoice != null && invoice.getOpenAmt().signum() > 0)
					invoices.add(invoice);
			}
			if (invoices.size() > 0) {
				String msg = null;
				MInvoice firstInvoice = invoices.get(0);
				bsi.setC_BPartner_ID(firstInvoice.getC_BPartner_ID());
				if (invoices.size() == 1) {
					// found one invoice
					if (bsl.getTrxAmt().compareTo(firstInvoice.getOpenAmt()) == 0) {
						bsi.setC_Invoice_ID(firstInvoice.getC_Invoice_ID());
						msg = Msg.getMsg(bsl.getCtx(), "BXS_ExactMatch");
					} else {
						DecimalFormat df = DisplayType.getNumberFormat(DisplayType.Amount);
						String openAmt = df.format(firstInvoice.getOpenAmt());
						msg = Msg.getMsg(bsl.getCtx(), "BXS_MatchInvoiceNotAmount", new Object[] {firstInvoice.getDocumentNo(), openAmt});
					}
				} else {
					// multiple invoices, a payment with multiple allocations must be created
					StringBuilder invoicesStr = new StringBuilder();
					for (MInvoice invoice : invoices) {
						if (invoicesStr.length() > 0)
							invoicesStr.append(", ");
						invoicesStr.append(invoice.getDocumentNo());
					}
					msg = Msg.getMsg(bsl.getCtx(), "BXS_MultiInvoiceMatch", new Object[] {invoicesStr.toString()});
				}
				addDescription(bsl, msg);
			}

		} else if (bsl.getTrxAmt().signum() < 0) {
			// TODO: match a vendor payment

		}
	}

	private void addDescription(MBankStatementLine bsl, String msg) {
		String description = bsl.getDescription();
		if (description == null)
			description = "";
		description = description.replaceAll("^¡ .* ¡ ", ""); // remove previous notice
		description = "¡ " + msg + " ¡ " + description;
		bsl.setDescription(description);
	}

	/**
	 * Search within the memo field for invoice numbers that matches with pending invoices
	 * @param bsi Bank Statement Match Info
	 * @param eftMemo The memo field filled with Hibiscus Zweck+Zweck2+Zweck3
	 * @param trxAmt The transaction amount of the statement line
	 */
	private List<String> searchInvoiceInMemo(String eftMemo) {
		List<String> invoiceArray = new ArrayList<String>();
		// Example for BXS_SALES_INVOICE_MATCH_REGEX
		// If the invoice number is expected to be 12 digits starting with 4802 the regex would be: (4802[0-9]{8})
		// multiple patterns can be added separated by comma
		String patterns = MSysConfig.getValue("BXS_SALES_INVOICE_MATCH_REGEX", Env.getAD_Client_ID(Env.getCtx()));
		if (patterns == null)
			throw new AdempiereException("First you need to configure the SysConfig BXS_SALES_INVOICE_MATCH_REGEX");
		for (String pattern : patterns.split(",")) {
			addPatternToArray(invoiceArray, pattern, eftMemo);
			// The EftMemo field comes with newlines
			// Sometimes the invoice number is split between two lines
			// for example: the invoice number 480202167177 - can come complete, or split in two lines, like 48020216\n7177
			// so, a second round of pattern check is done removing the newlines
			addPatternToArray(invoiceArray, pattern, eftMemo.replaceAll("\n", ""));
		}
		return invoiceArray;
	}

	private void addPatternToArray(List<String> invoiceArray, String pattern, String eftMemo) {
		Pattern p = Pattern.compile(pattern);
		Matcher m = p.matcher(eftMemo);
		while (m.find()) {
		    String matched = m.group(1);
		    if (! invoiceArray.contains(matched))
		    	invoiceArray.add(matched);
		}
	}

	/**
	 * 	Match Bank Statement Import Line
	 *	@param ibs bank statement import line
	 *	@return found matches or null
	 */
	@Override
	public BankStatementMatchInfo findMatch(X_I_BankStatement ibs) {
		log.warning("Not implemented for import bank statement");
		return null;
	}

}
